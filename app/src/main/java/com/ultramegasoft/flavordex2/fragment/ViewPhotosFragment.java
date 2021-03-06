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
package com.ultramegasoft.flavordex2.fragment;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.ultramegasoft.flavordex2.R;
import com.ultramegasoft.flavordex2.dialog.ConfirmationDialog;
import com.ultramegasoft.flavordex2.provider.Tables;
import com.ultramegasoft.flavordex2.util.EntryUtils;
import com.ultramegasoft.flavordex2.util.PhotoUtils;
import com.ultramegasoft.flavordex2.widget.PhotoHolder;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

/**
 * Fragment to display the photos for a journal entry.
 *
 * @author Steve Guidetti
 */
public class ViewPhotosFragment extends AbsPhotosFragment
        implements LoaderManager.LoaderCallbacks<Cursor> {
    /**
     * Request codes for external Activities
     */
    private static final int REQUEST_DELETE_IMAGE = 700;
    private static final int REQUEST_LOCATE_IMAGE = 701;

    /**
     * Keys for the saved state
     */
    private static final String STATE_CURRENT_ITEM = "current_item";

    /**
     * The database ID for this entry
     */
    private long mEntryId;

    /**
     * The currently displayed photo
     */
    private int mCurrentItem = -1;

    /**
     * Views for this Fragment
     */
    private ViewPager mPager;
    private LinearLayout mNoDataLayout;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Bundle args = getArguments();
        if(args != null) {
            mEntryId = args.getLong(ViewEntryFragment.ARG_ENTRY_ID);
        }

        if(savedInstanceState != null) {
            mCurrentItem = savedInstanceState.getInt(STATE_CURRENT_ITEM, mCurrentItem);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        if(!isMediaReadable()) {
            return super.onCreateView(inflater, container, savedInstanceState);
        }

        final View root = inflater.inflate(R.layout.fragment_entry_photos, container, false);

        mPager = root.findViewById(R.id.pager);
        mPager.setAdapter(new PagerAdapter());

        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if(!isMediaReadable()) {
            return;
        }
        if(getPhotos().isEmpty()) {
            getLoaderManager().initLoader(0, null, this);
        } else {
            notifyDataChanged();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_CURRENT_ITEM, mCurrentItem);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if(mPager != null) {
            mCurrentItem = mPager.getCurrentItem();
            mPager = null;
        }
        mNoDataLayout = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        setHasOptionsMenu(false);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.view_photos_menu, menu);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        final boolean showAdd = isMediaReadable();
        menu.findItem(R.id.menu_add_photo).setEnabled(showAdd).setVisible(showAdd);
        menu.findItem(R.id.menu_select_photo).setEnabled(showAdd);

        final boolean showTake = showAdd && hasCamera();
        menu.findItem(R.id.menu_take_photo).setEnabled(showTake);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(resultCode == Activity.RESULT_OK) {
            switch(requestCode) {
                case REQUEST_DELETE_IMAGE:
                    removePhoto(mCurrentItem);
                    break;
                case REQUEST_LOCATE_IMAGE:
                    if(data != null) {
                        replacePhoto(data.getData());
                    }
                    break;
            }
        }
    }

    @Override
    protected void onPhotoAdded(@NonNull PhotoHolder photo) {
        notifyDataChanged();
        mPager.setCurrentItem(getPhotos().size() - 1, true);

        final Context context = getContext();
        if(context != null) {
            new PhotoSaver(context, mEntryId, photo).execute();
        }
    }

    @Override
    protected void onPhotoRemoved(@NonNull PhotoHolder photo) {
        notifyDataChanged();

        final Context context = getContext();
        if(context != null) {
            new PhotoDeleter(context, photo).execute();
        }
    }

    /**
     * Show the message that there are no photos for this entry along with buttons to add one.
     */
    @SuppressWarnings("ConstantConditions")
    private void showNoDataLayout() {
        final AppCompatActivity activity = (AppCompatActivity)getActivity();
        if(mNoDataLayout == null) {
            mNoDataLayout =
                    (LinearLayout)((ViewStub)activity.findViewById(R.id.no_photos)).inflate();

            final Button btnTakePhoto = mNoDataLayout.findViewById(R.id.button_take_photo);
            if(hasCamera()) {
                btnTakePhoto.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        takePhoto();
                    }
                });
            } else {
                btnTakePhoto.setEnabled(false);
            }

            final Button btnAddPhoto = mNoDataLayout.findViewById(R.id.button_add_photo);
            btnAddPhoto.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    addPhotoFromGallery();
                }
            });
        }

        mNoDataLayout.setVisibility(View.VISIBLE);
        activity.invalidateOptionsMenu();
    }

    /**
     * Show a confirmation dialog to delete the shown image.
     */
    public void confirmDeletePhoto() {
        final FragmentManager fm = getFragmentManager();
        if(fm == null || getPhotos().isEmpty()) {
            return;
        }

        mCurrentItem = mPager.getCurrentItem();
        ConfirmationDialog.showDialog(fm, this, REQUEST_DELETE_IMAGE,
                getString(R.string.title_remove_photo),
                getString(R.string.message_confirm_remove_photo), R.drawable.ic_delete);
    }

    /**
     * Delete the shown image.
     */
    public void deletePhoto() {
        if(getPhotos().isEmpty()) {
            return;
        }
        removePhoto(mPager.getCurrentItem());
    }

    /**
     * Locate a missing image.
     */
    public void locatePhoto() {
        final Fragment parent = getParentFragment();
        if(parent == null || getPhotos().isEmpty()) {
            return;
        }

        mCurrentItem = mPager.getCurrentItem();
        final Intent intent = PhotoUtils.getSelectPhotoIntent();
        parent.startActivityForResult(intent, REQUEST_LOCATE_IMAGE);
    }

    /**
     * Replace the currently shown image.
     *
     * @param uri The Uri for the new image
     */
    private void replacePhoto(@Nullable Uri uri) {
        final Context context = getContext();
        if(context == null || uri == null || getPhotos().isEmpty()) {
            return;
        }

        final PhotoHolder photo = getPhotos().get(mCurrentItem);
        if(photo != null) {
            photo.uri = uri;
            photo.hash = null;
            new PhotoSaver(context, mEntryId, photo).execute();
            notifyDataChanged();
        }
    }

    /**
     * Called whenever the list of photos might have been changed. This notifies the ViewPager's
     * Adapter and the ActionBar.
     */
    private void notifyDataChanged() {
        if(mPager != null) {
            final PagerAdapter adapter = (PagerAdapter)mPager.getAdapter();
            if(adapter != null) {
                adapter.notifyDataSetChanged();
            }
        }

        if(!getPhotos().isEmpty()) {
            if(mNoDataLayout != null) {
                mNoDataLayout.setVisibility(View.GONE);
            }
        } else {
            showNoDataLayout();
        }

        final AppCompatActivity activity = (AppCompatActivity)getActivity();
        if(activity != null) {
            activity.invalidateOptionsMenu();
        }
    }

    @SuppressWarnings("ConstantConditions")
    @NonNull
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        final Context context = getContext();
        if(context == null) {
            return null;
        }

        final Uri uri =
                Uri.withAppendedPath(Tables.Entries.CONTENT_ID_URI_BASE, mEntryId + "/photos");
        final String[] projection = new String[] {
                Tables.Photos._ID,
                Tables.Photos.HASH,
                Tables.Photos.PATH,
                Tables.Photos.POS
        };
        final String where = Tables.Photos.PATH + " NOT NULL";
        final String order = Tables.Photos.POS + " ASC";
        return new CursorLoader(context, uri, projection, where, null, order);
    }

    @Override
    public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor data) {
        final ArrayList<PhotoHolder> photos = getPhotos();
        photos.clear();
        long id;
        String hash;
        String path;
        int pos;
        Uri uri;
        while(data.moveToNext()) {
            id = data.getLong(data.getColumnIndex(Tables.Photos._ID));
            hash = data.getString(data.getColumnIndex(Tables.Photos.HASH));
            path = data.getString(data.getColumnIndex(Tables.Photos.PATH));
            pos = data.getInt(data.getColumnIndex(Tables.Photos.POS));
            uri = PhotoUtils.parsePath(path);
            if(uri != null) {
                photos.add(new PhotoHolder(id, hash, uri, pos));
            }
        }

        notifyDataChanged();

        getLoaderManager().destroyLoader(0);
    }

    @Override
    public void onLoaderReset(@NonNull Loader<Cursor> loader) {
    }

    /**
     * Adapter for the ViewPager.
     */
    private class PagerAdapter extends FragmentStatePagerAdapter {
        /**
         * The data backing the Adapter
         */
        @NonNull
        private final ArrayList<PhotoHolder> mData;

        PagerAdapter() {
            super(getChildFragmentManager());
            mData = getPhotos();
        }

        @Override
        public Fragment getItem(int position) {
            final Bundle args = new Bundle();
            args.putParcelable(PhotoFragment.ARG_URI, mData.get(position).uri);
            return instantiate(getContext(), PhotoFragment.class.getName(), args);
        }

        @Override
        public int getCount() {
            return mData.size();
        }

        @Override
        @SuppressWarnings("MethodDoesntCallSuperMethod")
        public int getItemPosition(@NonNull Object object) {
            return POSITION_NONE;
        }
    }

    /**
     * Task to insert a photo into the database in the background.
     */
    private static class PhotoSaver extends AsyncTask<Void, Void, Boolean> {
        /**
         * The Context reference
         */
        @NonNull
        private final WeakReference<Context> mContext;

        /**
         * The entry ID to assign the photo to
         */
        private final long mEntryId;

        /**
         * The photo to save
         */
        @NonNull
        private final PhotoHolder mPhoto;

        /**
         * @param context The Context
         * @param entryId The entry ID
         * @param photo   The photo to save
         */
        PhotoSaver(@NonNull Context context, long entryId, @NonNull PhotoHolder photo) {
            mContext = new WeakReference<>(context.getApplicationContext());
            mEntryId = entryId;
            mPhoto = photo;
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            final Context context = mContext.get();
            if(context == null) {
                return false;
            }

            final ContentResolver cr = context.getContentResolver();
            Uri uri = PhotoUtils.getFileUri(cr, mPhoto.uri);
            if(uri == null) {
                return false;
            }
            mPhoto.uri = uri;

            if(mPhoto.hash == null) {
                mPhoto.hash = PhotoUtils.getMD5Hash(cr, mPhoto.uri);
            }

            final ContentValues values = new ContentValues();
            values.put(Tables.Photos.HASH, mPhoto.hash);
            values.put(Tables.Photos.PATH, mPhoto.uri.getLastPathSegment());
            values.put(Tables.Photos.POS, mPhoto.pos);

            if(mPhoto.id > 0) {
                uri = ContentUris.withAppendedId(Tables.Photos.CONTENT_ID_URI_BASE, mPhoto.id);
                if(cr.update(uri, values, null, null) < 1) {
                    return false;
                }
            } else {
                uri = Uri.withAppendedPath(Tables.Entries.CONTENT_ID_URI_BASE,
                        mEntryId + "/photos");
                uri = cr.insert(uri, values);
                if(uri == null) {
                    return false;
                }
                mPhoto.id = Long.valueOf(uri.getLastPathSegment());
            }

            PhotoUtils.deleteThumb(context, mEntryId);

            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);

            if(!result) {
                final Context context = mContext.get();
                if(context != null) {
                    Toast.makeText(context, R.string.error_insert_photo, Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    /**
     * Task to delete a photo from the database in the background.
     */
    private static class PhotoDeleter extends AsyncTask<Void, Void, Void> {
        /**
         * The Context reference
         */
        @NonNull
        private final WeakReference<Context> mContext;

        /**
         * The photo to delete
         */
        @NonNull
        private final PhotoHolder mPhoto;

        /**
         * @param context The Context
         * @param photo   The photo to delete
         */
        PhotoDeleter(@NonNull Context context, @NonNull PhotoHolder photo) {
            mContext = new WeakReference<>(context.getApplicationContext());
            mPhoto = photo;
        }

        @Override
        protected Void doInBackground(Void... params) {
            final Context context = mContext.get();
            if(context == null) {
                return null;
            }

            EntryUtils.deletePhoto(context, mPhoto.id);

            return null;
        }
    }
}
