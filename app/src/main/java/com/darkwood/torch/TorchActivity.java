package com.darkwood.torch;

import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

public class TorchActivity extends AppCompatActivity implements TorchController.TorchListener {
    private static final String TAG = "TorchActivity";

    private ImageView mTorchBg;
    private ImageView mTorchButton;

    private TorchController mTorchController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTorchBg = findViewById(R.id.torch_bg);
        mTorchButton = findViewById(R.id.torch_button);
        mTorchButton.setClickable(true);

        mTorchController = new TorchController(this);
        mTorchController.onCreate(this, true);

        mTorchButton.setOnClickListener(v -> mTorchController.setTorch(!mTorchController.isTorchEnabled()));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mTorchController.onDestroy(this);
    }

    @Override
    public void onTorchStateChanged(boolean enabled) {
        Log.d(TAG, "onTorchStateChanged enabled : " + enabled);

        if (!mTorchController.isTorchAvailable()) {
            return;
        }
        if (enabled) {
            mTorchButton.setImageResource(R.drawable.torch_bt_on_normal);
            mTorchBg.setImageResource(R.drawable.torch_bg_on);
        } else {
            mTorchButton.setImageResource(R.drawable.torch_bt_off);
            mTorchBg.setImageResource(R.drawable.torch_bg_off);
        }
    }

    @Override
    public void onTorchError() {
        Log.d(TAG, "onTorchError");
    }

    @Override
    public void onTorchAvailabilityChanged(boolean available) {
        Log.d(TAG, "onTorchAvailabilityChanged available : " + available);

        if (!available) {
            mTorchButton.setImageResource(R.drawable.torch_bt_disabled);
            mTorchButton.setClickable(false);
        } else {
            mTorchButton.setImageResource(R.drawable.torch_bt_off);
            mTorchButton.setClickable(true);
        }
    }
}