package com.ultramegasoft.flavordex2;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import com.ultramegasoft.flavordex2.provider.Tables;
import com.ultramegasoft.flavordex2.widget.RadarEditWidget;
import com.ultramegasoft.flavordex2.widget.RadarHolder;
import com.ultramegasoft.flavordex2.widget.RadarView;

import java.util.ArrayList;

/**
 * Fragment to display the flavor radar chart of a journal entry.
 *
 * @author Steve Guidetti
 */
public class ViewFlavorsFragment extends Fragment implements LoaderManager.LoaderCallbacks<Cursor> {
    /**
     * Keys for the saved state of this fragment
     */
    private static final String STATE_EDIT_MODE = "edit_mode";
    private static final String STATE_DATA = "flavor_data";

    /**
     * The views from the fragment's layout
     */
    private RadarView mRadarView;
    private RadarEditWidget mEditWidget;

    /**
     * Animations for the editing layout
     */
    private Animation mInAnimation;
    private Animation mOutAnimation;

    /**
     * The entry's flavor data
     */
    private ArrayList<RadarHolder> mData;

    /**
     * Whether the radar chart is in edit mode
     */
    private boolean mEditMode;

    /**
     * The database id for this entry
     */
    private long mEntryId;

    public ViewFlavorsFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mEntryId = getArguments().getLong(ViewEntryFragment.ARG_ITEM_ID);
        setHasOptionsMenu(true);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if(savedInstanceState != null) {
            mData = savedInstanceState.getParcelableArrayList(STATE_DATA);
            mEditMode = savedInstanceState.getBoolean(STATE_EDIT_MODE, false);
            mRadarView.setVisibility(View.VISIBLE);
        } else if(mData == null) {
            getLoaderManager().initLoader(0, null, this);
        } else {
            mRadarView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View root = inflater.inflate(R.layout.fragment_view_flavors, container, false);

        mRadarView = (RadarView)root.findViewById(R.id.radar);
        mRadarView.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) {
                if(!mRadarView.isInteractive()) {
                    setEditMode(true, true);
                    return true;
                }
                return false;
            }
        });

        mEditWidget = (RadarEditWidget)root.findViewById(R.id.edit_widget);
        mEditWidget.setTarget(mRadarView);
        mEditWidget.setOnButtonClickListener(new RadarEditWidget.OnButtonClickListener() {
            @Override
            public void onSave() {
                saveData();
            }

            @Override
            public void onCancel() {
                cancelEdit();
            }
        });

        return root;
    }

    @Override
    public void onViewStateRestored(Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        setEditMode(mEditMode, false);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mRadarView = null;
        mEditWidget = null;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_EDIT_MODE, mEditMode);
        outState.putParcelableArrayList(STATE_DATA, mData);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.view_flavor_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int id = item.getItemId();
        if(id == R.id.menu_edit_flavor) {
            setEditMode(true, true);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Enable or disable the radar view's edit mode and show or hide the editing layout.
     *
     * @param editMode Whether to enable edit mode
     * @param animate  Whether to animate the edit interface sliding in
     */
    private void setEditMode(boolean editMode, boolean animate) {
        if(animate && mInAnimation == null) {
            mInAnimation = AnimationUtils.loadAnimation(getActivity(), R.anim.flavor_edit_in);
            mOutAnimation = AnimationUtils.loadAnimation(getActivity(), R.anim.flavor_edit_out);
        }

        if(editMode) {
            mEditWidget.setVisibility(View.VISIBLE);
            if(animate) {
                mEditWidget.startAnimation(mInAnimation);
            }
        } else {
            if(mEditWidget != null) {
                if(animate) {
                    mEditWidget.startAnimation(mOutAnimation);
                }
                mEditWidget.setVisibility(View.INVISIBLE);
            }
        }

        mRadarView.setInteractive(editMode);
        mEditMode = editMode;
    }

    /**
     * Save the current flavor data to the database
     */
    private void saveData() {
        setEditMode(false, true);
        mData = mRadarView.getData();
        new DataSaver(getActivity().getApplicationContext(), mEntryId).execute(mData);
    }

    /**
     * Reset the radar chart to the original data and disable edit mode.
     */
    private void cancelEdit() {
        setEditMode(false, true);
        mRadarView.setData(mData);
    }

    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        final Uri uri = Uri.withAppendedPath(Tables.Entries.CONTENT_ID_URI_BASE, mEntryId + "/flavor");
        final String[] projection = new String[] {
                Tables.EntriesFlavors.FLAVOR,
                Tables.Flavors.NAME,
                Tables.EntriesFlavors.VALUE
        };
        return new CursorLoader(getActivity(), uri, projection, null, null,
                Tables.EntriesFlavors._ID + " ASC");
    }

    public void onLoadFinished(Loader<Cursor> loader, Cursor d) {
        final ArrayList<RadarHolder> flavorValues = new ArrayList<>();

        while(d.moveToNext()) {
            flavorValues.add(new RadarHolder(d.getLong(0), d.getString(1), d.getInt(2)));
        }

        mData = flavorValues;
        mRadarView.setData(flavorValues);
        mRadarView.setVisibility(View.VISIBLE);

        getLoaderManager().destroyLoader(0);
    }

    public void onLoaderReset(Loader<Cursor> loader) {
    }

    /**
     * Async task to save the data.
     */
    private static class DataSaver extends AsyncTask<ArrayList<RadarHolder>, Void, Void> {
        /**
         * The context used to access the ContentManager
         */
        private final Context mContext;

        /**
         * The entry id to save the flavors to
         */
        private final long mEntryId;

        /**
         * @param context The context
         * @param entryId The entry to save flavors to
         */
        public DataSaver(Context context, long entryId) {
            mContext = context.getApplicationContext();
            mEntryId = entryId;
        }

        @Override
        protected Void doInBackground(ArrayList<RadarHolder>... arg0) {
            final ArrayList<RadarHolder> data = arg0[0];
            final ContentResolver cr = mContext.getContentResolver();
            Uri uri = Uri.withAppendedPath(Tables.Entries.CONTENT_ID_URI_BASE, mEntryId + "/flavor");

            // do a bulk insert of all values (replaces existing)
            final ContentValues[] values = new ContentValues[data.size()];
            for(int i = 0; i < data.size(); i++) {
                values[i] = new ContentValues();
                values[i].put(Tables.EntriesFlavors.FLAVOR, data.get(i).id);
                values[i].put(Tables.EntriesFlavors.VALUE, data.get(i).value);
            }
            cr.bulkInsert(uri, values);

            return null;
        }
    }

}