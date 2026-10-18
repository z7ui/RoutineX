package com.z7ui.probe.shortcut;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends Activity {

    private static final String PKG = "com.byd.aeri.caranywhere";
    private static final String SIGN_ID = "3";

    private TextView out;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        out = findViewById(R.id.tvOut);

        Button enumBtn = findViewById(R.id.btnEnum);
        enumBtn.setOnClickListener(v -> {
            String r = Probe.run(this, "enum", PKG, SIGN_ID);
            out.append(r + "\n");
        });

        Button launchBtn = findViewById(R.id.btnLaunch);
        launchBtn.setOnClickListener(v -> {
            String r = Probe.run(this, "launch", PKG, SIGN_ID);
            out.append(r + "\n");
        });

        Button clear = findViewById(R.id.btnClear);
        clear.setOnClickListener(v -> out.setText(""));
    }
}
