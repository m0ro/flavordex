/*
 * The MIT License (MIT)
 * Copyright © 2016 Steve Guidetti
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the “Software”), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.ultramegasoft.flavordex2;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;

import com.ultramegasoft.flavordex2.beer.EditBeerInfoFragment;
import com.ultramegasoft.flavordex2.coffee.EditCoffeeInfoFragment;
import com.ultramegasoft.flavordex2.fragment.EditInfoFragment;
import com.ultramegasoft.flavordex2.provider.Tables;
import com.ultramegasoft.flavordex2.whiskey.EditWhiskeyInfoFragment;
import com.ultramegasoft.flavordex2.widget.EntryHolder;
import com.ultramegasoft.flavordex2.widget.ExtraFieldHolder;
import com.ultramegasoft.flavordex2.wine.EditWineInfoFragment;

import java.lang.ref.WeakReference;

/**
 * Activity for editing a journal entry.
 *
 * @author Steve Guidetti
 */
public class EditEntryActivity extends AppCompatActivity {
    /**
     * Keys for the Intent extras
     */
    private static final String EXTRA_ENTRY_ID = "entry_id";
    private static final String EXTRA_ENTRY_CAT = "entry_cat";

    /**
     * The ID for the entry being edited
     */
    private long mEntryId;

    /**
     * Start the Activity to edit an entry.
     *
     * @param context  The Context
     * @param entryId  The ID for the entry to edit
     * @param entryCat The name of the entry category
     */
    public static void startActivity(@NonNull Context context, long entryId,
                                     @Nullable String entryCat) {
        final Intent intent = new Intent(context, EditEntryActivity.class);
        intent.putExtra(EXTRA_ENTRY_ID, entryId);
        intent.putExtra(EXTRA_ENTRY_CAT, entryCat);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final ActionBar actionBar = getSupportActionBar();
        if(actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        mEntryId = getIntent().getLongExtra(EXTRA_ENTRY_ID, mEntryId);
        if(savedInstanceState == null) {
            final Bundle args = new Bundle();
            args.putLong(EditInfoFragment.ARG_ENTRY_ID, mEntryId);

            final Fragment fragment = getFragment();
            fragment.setArguments(args);

            getSupportFragmentManager().beginTransaction().add(android.R.id.content, fragment)
                    .commit();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.entry_edit_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        final Fragment fragment =
                getSupportFragmentManager().findFragmentById(android.R.id.content);
        if(fragment instanceof EditInfoFragment) {
            menu.findItem(R.id.menu_save).setEnabled(!((EditInfoFragment)fragment).isLoading());
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            case R.id.menu_save:
                saveData();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Get the Fragment based on the entry category.
     *
     * @return The Fragment object
     */
    @NonNull
    private EditInfoFragment getFragment() {
        final String cat = getIntent().getStringExtra(EXTRA_ENTRY_CAT);

        if(FlavordexApp.CAT_BEER.equals(cat)) {
            return new EditBeerInfoFragment();
        }
        if(FlavordexApp.CAT_WINE.equals(cat)) {
            return new EditWineInfoFragment();
        }
        if(FlavordexApp.CAT_WHISKEY.equals(cat)) {
            return new EditWhiskeyInfoFragment();
        }
        if(FlavordexApp.CAT_COFFEE.equals(cat)) {
            return new EditCoffeeInfoFragment();
        }

        return new EditInfoFragment();
    }

    /**
     * Save the changes for the entry.
     */
    private void saveData() {
        final EditInfoFragment fragment = (EditInfoFragment)getSupportFragmentManager()
                .findFragmentById(android.R.id.content);
        if(fragment == null || !fragment.isValid()) {
            return;
        }

        final EntryHolder entry = new EntryHolder();
        fragment.getData(entry);
        new DataSaver(this, entry).execute();
        finish();
    }

    /**
     * Task for saving an entry in the background.
     */
    private static class DataSaver extends AsyncTask<Void, Void, Void> {
        /**
         * The Context reference
         */
        @NonNull
        private final WeakReference<Context> mContext;

        /**
         * The entry to save
         */
        @NonNull
        private final EntryHolder mEntry;

        /**
         * @param context The Context
         * @param entry   The entry to save
         */
        DataSaver(@NonNull Context context, @NonNull EntryHolder entry) {
            mContext = new WeakReference<>(context.getApplicationContext());
            mEntry = entry;
        }

        @Override
        protected Void doInBackground(Void... params) {
            final Context context = mContext.get();
            if(context == null) {
                return null;
            }

            final ContentResolver cr = context.getContentResolver();
            final Uri uri =
                    ContentUris.withAppendedId(Tables.Entries.CONTENT_ID_URI_BASE, mEntry.id);

            final ContentValues values = new ContentValues();
            values.put(Tables.Entries.TITLE, mEntry.title);
            values.put(Tables.Entries.MAKER, mEntry.maker);
            values.put(Tables.Entries.ORIGIN, mEntry.origin);
            values.put(Tables.Entries.PRICE, mEntry.price);
            values.put(Tables.Entries.LOCATION, mEntry.location);
            values.put(Tables.Entries.DATE, mEntry.date);
            values.put(Tables.Entries.RATING, mEntry.rating);
            values.put(Tables.Entries.NOTES, mEntry.notes);
            cr.update(uri, values, null, null);

            updateExtras(cr, uri);

            return null;
        }

        /**
         * Update the entry extra fields.
         *
         * @param cr       The ContentResolver
         * @param entryUri The Uri for the entry
         */
        private void updateExtras(@NonNull ContentResolver cr, @NonNull Uri entryUri) {
            final Uri uri = Uri.withAppendedPath(entryUri, "extras");
            final ContentValues values = new ContentValues();
            for(ExtraFieldHolder extra : mEntry.getExtras()) {
                if(!extra.preset && TextUtils.isEmpty(extra.value)) {
                    cr.delete(uri, Tables.EntriesExtras.EXTRA + " = " + extra.id, null);
                    continue;
                }
                values.put(Tables.EntriesExtras.EXTRA, extra.id);
                values.put(Tables.EntriesExtras.VALUE, extra.value);
                cr.insert(uri, values);
            }
        }
    }
}
