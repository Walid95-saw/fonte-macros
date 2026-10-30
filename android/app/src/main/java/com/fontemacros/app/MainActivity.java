package com.fontemacros.app;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        // le greffon vit dans l'application, il faut donc l'enregistrer avant le pont
        registerPlugin(SantePlugin.class);
        super.onCreate(savedInstanceState);
    }
}
