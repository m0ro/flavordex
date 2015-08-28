package com.ultramegasoft.flavordex2.whiskey;

import android.content.res.Configuration;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;

import com.ultramegasoft.flavordex2.AddEntryInfoFragment;
import com.ultramegasoft.flavordex2.R;
import com.ultramegasoft.flavordex2.provider.Tables;

import java.util.HashMap;

/**
 * Fragment for adding details for a new whiskey entry.
 *
 * @author Steve Guidetti
 */
public class AddWhiskeyInfoFragment extends AddEntryInfoFragment {
    /**
     * The views for the form fields
     */
    private AutoCompleteTextView mTxtType;
    private EditText mTxtAge;
    private EditText mTxtABV;

    @NonNull
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View root = super.onCreateView(inflater, container, savedInstanceState);

        mTxtType = (AutoCompleteTextView)root.findViewById(R.id.entry_type);
        mTxtType.setAdapter(ArrayAdapter.createFromResource(getActivity(), R.array.whiskey_types,
                android.R.layout.simple_dropdown_item_1line));

        mTxtAge = (EditText)root.findViewById(R.id.entry_stats_age);
        mTxtAge.setRawInputType(Configuration.KEYBOARD_QWERTY);

        mTxtABV = (EditText)root.findViewById(R.id.entry_stats_abv);
        mTxtABV.setRawInputType(Configuration.KEYBOARD_QWERTY);

        return root;
    }

    @Override
    protected int getLayoutId() {
        return R.layout.fragment_add_info_whiskey;
    }

    @Override
    protected void addExtraRow(String name) {
    }

    @Override
    protected void readExtras(HashMap<String, String> values) {
        values.put(Tables.Extras.Whiskey.STYLE, mTxtType.getText().toString());
        values.put(Tables.Extras.Whiskey.STATS_AGE, mTxtAge.getText().toString());
        values.put(Tables.Extras.Whiskey.STATS_ABV, mTxtABV.getText().toString());
    }
}