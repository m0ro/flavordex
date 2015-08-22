package com.ultramegasoft.flavordex2.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AlertDialog;

/**
 * Dialog to show confirmation messages.
 *
 * @author Steve Guidetti
 */
public class ConfirmationDialog extends DialogFragment {
    /**
     * Tag to identify the fragment
     */
    private static final String TAG = "ConfirmationDialog";

    /**
     * Arguments for the fragment
     */
    public static final String ARG_TITLE = "title";
    public static final String ARG_MESSAGE = "message";
    public static final String ARG_DATA = "data";

    /**
     * Show a confirmation dialog.
     *
     * @param fm          The FragmentManager to use
     * @param target      The fragment to notify of the result
     * @param requestCode A number to identify this request
     * @param title       The dialog title
     * @param message     The dialog message
     */
    public static void showDialog(FragmentManager fm, Fragment target, int requestCode,
                                  String title, String message) {
        showDialog(fm, target, requestCode, title, message, null);
    }

    /**
     * Show a confirmation dialog.
     *
     * @param fm          The FragmentManager to use
     * @param target      The fragment to notify of the result
     * @param requestCode A number to identify this request
     * @param title       The dialog title
     * @param message     The dialog message
     * @param data        An Intent to store additional data
     */
    public static void showDialog(FragmentManager fm, Fragment target, int requestCode,
                                  String title, String message, Intent data) {
        final DialogFragment fragment = new ConfirmationDialog();
        fragment.setTargetFragment(target, requestCode);

        final Bundle args = new Bundle();
        args.putString(ARG_TITLE, title);
        args.putString(ARG_MESSAGE, message);
        args.putParcelable(ARG_DATA, data);
        fragment.setArguments(args);

        fragment.show(fm, TAG);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final Bundle args = getArguments();
        return new AlertDialog.Builder(getActivity())
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle(args.getString(ARG_TITLE))
                .setMessage(args.getString(ARG_MESSAGE))
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        final Fragment target = getTargetFragment();
                        if(target != null) {
                            final Intent data = args.getParcelable(ARG_DATA);
                            target.onActivityResult(getTargetRequestCode(),
                                    Activity.RESULT_OK, data);
                        }
                        dismiss();
                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        dialog.cancel();
                    }
                })
                .create();
    }
}