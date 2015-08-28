package com.ultramegasoft.flavordex2;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.ultramegasoft.flavordex2.beer.AddBeerInfoFragment;
import com.ultramegasoft.flavordex2.coffee.AddCoffeeInfoFragment;
import com.ultramegasoft.flavordex2.provider.Tables;
import com.ultramegasoft.flavordex2.whiskey.AddWhiskeyInfoFragment;
import com.ultramegasoft.flavordex2.wine.AddWineInfoFragment;

/**
 * The parent fragment for the entry creation pages.
 *
 * @author Steve Guidetti
 */
public class AddEntryFragment extends Fragment implements LoaderManager.LoaderCallbacks<Cursor> {
    /**
     * Keys for the fragment arguments
     */
    public static final String ARG_TYPE_ID = "type_id";

    /**
     * The type id from the arguments
     */
    private long mTypeId;

    /**
     * The name of the entry type
     */
    private String mTypeName;

    /**
     * The ViewPager containing the fragments
     */
    private ViewPager mPager;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mTypeId = getArguments().getLong(ARG_TYPE_ID, 0);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View root = inflater.inflate(R.layout.fragment_add_entry, container, false);
        mPager = (ViewPager)root.findViewById(R.id.pager);

        final Button saveButton = (Button)root.findViewById(R.id.button_save);
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveEntry();
            }
        });

        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        getLoaderManager().initLoader(0, null, this);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case android.R.id.home:
                final Fragment fragment = new TypeListFragment();
                getFragmentManager().beginTransaction().replace(android.R.id.content, fragment)
                        .commit();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Get the fragment class to use for adding the main details of the entry.
     *
     * @return The Fragment class
     */
    private Class<? extends AddEntryInfoFragment> getEntryInfoClass() {
        if(FlavordexApp.TYPE_BEER.equals(mTypeName)) {
            return AddBeerInfoFragment.class;
        }
        if(FlavordexApp.TYPE_WINE.equals(mTypeName)) {
            return AddWineInfoFragment.class;
        }
        if(FlavordexApp.TYPE_WHISKEY.equals(mTypeName)) {
            return AddWhiskeyInfoFragment.class;
        }
        if(FlavordexApp.TYPE_COFFEE.equals(mTypeName)) {
            return AddCoffeeInfoFragment.class;
        }

        return AddEntryInfoFragment.class;
    }

    /**
     * Insert the new entry into the database.
     */
    private void saveEntry() {
        final FragmentManager fm = getChildFragmentManager();
        boolean isValid = false;
        ContentValues entryInfo = null;
        ContentValues[] entryExtras = null;
        ContentValues[] entryFlavors = null;
        ContentValues[] entryPhotos = null;
        for(Fragment fragment : fm.getFragments()) {
            if(fragment instanceof AddEntryInfoFragment) {
                isValid = ((AddEntryInfoFragment)fragment).isValid();
                if(!isValid) {
                    break;
                }
                entryInfo = ((AddEntryInfoFragment)fragment).getData();
                entryExtras = ((AddEntryInfoFragment)fragment).getExtras();
                continue;
            }
            if(fragment instanceof AddEntryFlavorsFragment) {
                entryFlavors = ((AddEntryFlavorsFragment)fragment).getData();
                continue;
            }
            if(fragment instanceof AddEntryPhotosFragment) {
                entryPhotos = ((AddEntryPhotosFragment)fragment).getData();
            }
        }

        if(isValid && entryInfo != null) {
            new DataSaver(getActivity(), entryInfo, entryExtras, entryFlavors, entryPhotos)
                    .execute();
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        final Uri uri = ContentUris.withAppendedId(Tables.Types.CONTENT_ID_URI_BASE, mTypeId);
        return new CursorLoader(getActivity(), uri, null, null, null, null);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        if(data.moveToFirst()) {
            mTypeName = data.getString(data.getColumnIndex(Tables.Types.NAME));
            mPager.setAdapter(new PagerAdapter());
            final String name = FlavordexApp.getRealTypeName(getActivity(), mTypeName);
            final String title = getString(R.string.title_add_type_entry, name);
            ((AppCompatActivity)getActivity()).getSupportActionBar().setTitle(title);
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
    }

    /**
     * Adapter for the ViewPager
     */
    private class PagerAdapter extends FragmentPagerAdapter {
        /**
         * The list of fragments
         */
        private String[] mFragments = new String[] {
                AddEntryInfoFragment.class.getName(),
                AddEntryFlavorsFragment.class.getName(),
                AddEntryPhotosFragment.class.getName()
        };

        /**
         * Page title string resource ids
         */
        private int[] mPageNames = {
                R.string.title_add_entry,
                R.string.title_add_flavor,
                R.string.title_add_photos
        };

        public PagerAdapter() {
            super(getChildFragmentManager());
            mFragments[0] = getEntryInfoClass().getName();
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return getText(mPageNames[position]);
        }

        @Override
        public Fragment getItem(int position) {
            final Bundle args = new Bundle();
            args.putLong(ARG_TYPE_ID, mTypeId);

            return Fragment.instantiate(getActivity(), mFragments[position], args);
        }

        @Override
        public int getCount() {
            return mFragments.length;
        }
    }

    /**
     * Task for saving a new entry into the database in the background.
     */
    private static class DataSaver extends AsyncTask<Void, Void, Long> {
        /**
         * The ContentResolver to use for inserting data
         */
        private final ContentResolver mResolver;

        /**
         * Values for the entries table row
         */
        private final ContentValues mEntryInfo;

        /**
         * Values for the entries_extras table rows
         */
        private final ContentValues[] mEntryExtras;

        /**
         * Values for the entries_flavors table rows
         */
        private final ContentValues[] mEntryFlavors;

        /**
         * Values for the photos table rows
         */
        private final ContentValues[] mEntryPhotos;

        /**
         * @param context      The context
         * @param entryInfo    Values for the entries table row
         * @param entryExtras  Values for the entries_extras table rows
         * @param entryFlavors Values for the entries_flavors table rows
         * @param entryPhotos  Values for the photos table rows
         */
        public DataSaver(Context context, ContentValues entryInfo, ContentValues[] entryExtras,
                         ContentValues[] entryFlavors, ContentValues[] entryPhotos) {
            mResolver = context.getContentResolver();
            mEntryInfo = entryInfo;
            mEntryExtras = entryExtras;
            mEntryFlavors = entryFlavors;
            mEntryPhotos = entryPhotos;
        }

        @Override
        protected Long doInBackground(Void... params) {
            final Uri entryUri = mResolver.insert(Tables.Entries.CONTENT_URI, mEntryInfo);
            if(mEntryExtras != null) {
                mResolver.bulkInsert(Uri.withAppendedPath(entryUri, "/extras"), mEntryExtras);
            }
            if(mEntryFlavors != null) {
                mResolver.bulkInsert(Uri.withAppendedPath(entryUri, "/flavor"), mEntryFlavors);
            }
            if(mEntryPhotos != null) {
                mResolver.bulkInsert(Uri.withAppendedPath(entryUri, "/photos"), mEntryPhotos);
            }

            return Long.valueOf(entryUri.getLastPathSegment());
        }

        @Override
        protected void onPostExecute(Long entryId) {
        }
    }
}