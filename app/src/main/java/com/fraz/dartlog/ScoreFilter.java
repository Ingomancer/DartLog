package com.fraz.dartlog;

import android.text.InputFilter;
import android.text.Spanned;

public class ScoreFilter implements InputFilter {

    @Override
    public CharSequence filter(CharSequence source, int start, int end,
                               Spanned dest, int dstart, int dend) {
        Integer number = Integer.parseInt(dest.toString() + source);
        if (number > 180)
            return "";
        else
            return source;
    }
}
