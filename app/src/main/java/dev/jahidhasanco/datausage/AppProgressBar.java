package dev.jahidhasanco.datausage;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.view.Window;

import com.alauddin.datausagen.R;


@SuppressLint("NonConstantResourceId")
public class AppProgressBar {
    public static Dialog dialog;

    public static void showLoaderDialog(Context context) {
        try {
            if (dialog != null)
                if (dialog.isShowing())
                    dialog.dismiss();
            dialog = new Dialog(context);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            dialog.setCancelable(false);
            dialog.setContentView(R.layout.custom_loader);

            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
            dialog.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void hideLoaderDialog() {
        try {
            if (dialog != null && dialog.isShowing())
                dialog.dismiss();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
