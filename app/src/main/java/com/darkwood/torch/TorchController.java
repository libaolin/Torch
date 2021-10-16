package com.darkwood.torch;

import android.content.Context;
import android.content.Intent;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.Settings;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

public class TorchController {
    private static final String TAG = "TorchController";

    private static final String ACTION_FLASHLIGHT_CHANGED =
            "com.android.settings.flashlight.action.FLASHLIGHT_CHANGED";
    private static final String FLASHLIGHT_AVAILABLE = "flashlight_available";
    private static final String FLASHLIGHT_ENABLED = "flashlight_enabled";

    private static final int DISPATCH_ERROR = 0;
    private static final int DISPATCH_CHANGED = 1;
    private static final int DISPATCH_AVAILABILITY_CHANGED = 2;

    private final Context mContext;
    private final CameraManager mCameraManager;
    private final ArrayList<String> mAllCameraId = new ArrayList<>();
    private final ArrayList<WeakReference<TorchListener>> mListeners = new ArrayList<>(1);
    private String mCameraId;
    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private boolean mTorchEnabled;
    private boolean mTorchAvailable;
    private final CameraManager.TorchCallback mTorchCallback =
            new CameraManager.TorchCallback() {

                @Override
                public void onTorchModeUnavailable(String cameraId) {
                    if (mAllCameraId.contains(cameraId)) {
                        setCameraAvailable(false);
                        try {
                            Settings.Secure.putInt(
                                    mContext.getContentResolver(), FLASHLIGHT_AVAILABLE, 0);
                        } catch (Exception e) {
                            Log.d(TAG, "write Settings.Secure error");
                        }
                    }
                }

                @Override
                public void onTorchModeChanged(String cameraId, boolean enabled) {
                    if (cameraId != null && mAllCameraId.contains(cameraId) && (cameraId.equals(mCameraId))) {
                        //Log.d(TAG, "onTorchModeChanged cameraId : " + cameraId + ", enabled : " + enabled);
                        setCameraAvailable(true);
                        setTorchMode(enabled);
                        try {
                            Settings.Secure.putInt(
                                    mContext.getContentResolver(), FLASHLIGHT_AVAILABLE, 1);
                            Settings.Secure.putInt(
                                    mContext.getContentResolver(), FLASHLIGHT_ENABLED, enabled ? 1 : 0);
                        } catch (Exception e) {
                            Log.d(TAG, "write Settings.Secure error");
                        }

                        try {
                            mContext.sendBroadcast(new Intent(ACTION_FLASHLIGHT_CHANGED));
                        } catch (Exception e) {
                            Log.d(TAG, "sendBroadcast error");
                        }
                    }
                }

                private void setCameraAvailable(boolean available) {
                    boolean changed;
                    synchronized (TorchController.this) {
                        changed = mTorchAvailable != available;
                        mTorchAvailable = available;
                    }
                    if (changed) {
                        dispatchAvailabilityChanged(available);
                    }
                }

                private void setTorchMode(boolean enabled) {
                    boolean changed;
                    synchronized (TorchController.this) {
                        changed = mTorchEnabled != enabled;
                        mTorchEnabled = enabled;
                    }
                    if (changed) {
                        dispatchModeChanged(enabled);
                    }
                }
            };

    public TorchController(Context context) {
        mContext = context;
        mCameraManager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
    }

    public void onCreate(TorchListener listener, boolean enable) {
        Log.d(TAG, "onCreate");
        addCallback(listener);
        if (enable) {
            setTorch(true);
        }
    }

    public void setTorch(boolean enable) {
        Log.d(TAG, "setTorch : " + enable);
        boolean pendingError = false;
        synchronized (this) {
            if (mCameraId == null) return;
            if (mTorchEnabled != enable) {
                mTorchEnabled = enable;
                try {
                    mCameraManager.setTorchMode(mCameraId, enable);
                } catch (CameraAccessException e) {
                    Log.e(TAG, "Couldn't set torch mode", e);
                    mTorchEnabled = false;
                    pendingError = true;
                }
            }
        }
        dispatchModeChanged(mTorchEnabled);
        if (pendingError) {
            dispatchError();
        }
    }

    public void onDestroy(TorchListener listener) {
        Log.d(TAG, "onDestroy");
        setTorch(false);
        removeCallback(listener);
    }

    public synchronized boolean isTorchAvailable() {
        return mTorchAvailable;
    }

    public synchronized boolean isTorchEnabled() {
        return mTorchEnabled;
    }

    private void init() {
        if (mHandler == null) {
            mHandlerThread = new HandlerThread(TAG);
            mHandlerThread.start();
            mHandler = new Handler(mHandlerThread.getLooper());
        }

        try {
            mCameraId = getCameraId();
            if (mCameraId != null) {
                mCameraManager.registerTorchCallback(mTorchCallback, mHandler);
            }
        } catch (Throwable e) {
            mCameraId = null;
            Log.e(TAG, "Couldn't initialize.", e);
        }
    }

    private void deInit() {
        if (mCameraId != null) {
            mCameraManager.unregisterTorchCallback(mTorchCallback);
            mCameraId = null;
        }
        if (mHandlerThread != null) {
            mHandlerThread.quitSafely();
            mHandlerThread = null;
        }
        mHandler = null;
        mAllCameraId.clear();
    }

    private void addCallback(TorchListener l) {
        synchronized (mListeners) {
            init();
            cleanUpListenersLocked(l);
            mListeners.add(new WeakReference<>(l));
            l.onTorchAvailabilityChanged(mTorchAvailable);
            l.onTorchStateChanged(mTorchEnabled);
        }
    }

    private void removeCallback(TorchListener l) {
        synchronized (mListeners) {
            cleanUpListenersLocked(l);
            deInit();
        }
    }

    private String getCameraId() throws CameraAccessException {
        String[] ids = mCameraManager.getCameraIdList();
        mAllCameraId.clear();
        for (String id : ids) {
            CameraCharacteristics c = mCameraManager.getCameraCharacteristics(id);
            Boolean flashAvailable = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
            Integer lensFacing = c.get(CameraCharacteristics.LENS_FACING);
            if (flashAvailable != null && flashAvailable
                    && lensFacing != null && lensFacing == CameraCharacteristics.LENS_FACING_BACK) {
                mAllCameraId.add(id);
            }
        }
        return mAllCameraId.size() > 0 ? mAllCameraId.get(0) : null;
    }

    private void dispatchModeChanged(boolean enabled) {
        dispatchListeners(DISPATCH_CHANGED, enabled);
    }

    private void dispatchError() {
        dispatchListeners(DISPATCH_CHANGED, false /* argument (ignored) */);
    }

    private void dispatchAvailabilityChanged(boolean available) {
        dispatchListeners(DISPATCH_AVAILABILITY_CHANGED, available);
    }

    private void dispatchListeners(int message, boolean argument) {
        synchronized (mListeners) {
            final int N = mListeners.size();
            boolean cleanup = false;
            for (int i = 0; i < N; i++) {
                TorchListener l = mListeners.get(i).get();
                if (l != null) {
                    if (message == DISPATCH_ERROR) {
                        l.onTorchError();
                    } else if (message == DISPATCH_CHANGED) {
                        l.onTorchStateChanged(argument);
                    } else if (message == DISPATCH_AVAILABILITY_CHANGED) {
                        l.onTorchAvailabilityChanged(argument);
                    }
                } else {
                    cleanup = true;
                }
            }
            if (cleanup) {
                cleanUpListenersLocked(null);
            }
        }
    }

    private void cleanUpListenersLocked(TorchListener listener) {
        for (int i = mListeners.size() - 1; i >= 0; i--) {
            TorchListener found = mListeners.get(i).get();
            if (found == null || found == listener) {
                mListeners.remove(i);
            }
        }
    }

    public interface TorchListener {
        void onTorchStateChanged(boolean enabled);

        void onTorchError();

        void onTorchAvailabilityChanged(boolean available);
    }

}
