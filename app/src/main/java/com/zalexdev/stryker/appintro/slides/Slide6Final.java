package com.zalexdev.stryker.appintro.slides;


import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.zalexdev.stryker.R;
import com.zalexdev.stryker.utils.ExecutorBuilder;
import com.zalexdev.stryker.utils.Preferences;


public class Slide6Final extends Fragment {

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.slide6, container, false);
        Preferences.getInstance().setInstalled();
        Log.d("Slide6Final", "onCreateView: "+Preferences.getInstance().isInstalled());
        MaterialButton button = view.findViewById(R.id.login);
        button.setOnClickListener(view1 -> {

            ExecutorBuilder.runCommand("am force-stop com.zalexdev.stryker5 && am start -n com.zalexdev.stryker5/com.zalexdev.stryker.MainActivity");
            requireActivity().finishAffinity();
        });
        return view;
    }
}