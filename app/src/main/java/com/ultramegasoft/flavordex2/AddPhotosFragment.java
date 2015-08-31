package com.ultramegasoft.flavordex2;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.Toast;

import com.ultramegasoft.flavordex2.provider.Tables;
import com.ultramegasoft.flavordex2.util.BitmapCache;
import com.ultramegasoft.flavordex2.util.PhotoUtils;
import com.ultramegasoft.flavordex2.widget.ImageLoader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Fragment for adding photos to a new journal entry.
 *
 * @author Steve Guidetti
 */
public class AddPhotosFragment extends Fragment {
    /**
     * Keys for the saved state
     */
    private static final String STATE_PHOTOS = "photos";

    /**
     * Request codes for external activities
     */
    private static final int REQUEST_CAPTURE_IMAGE = 100;
    private static final int REQUEST_SELECT_IMAGE = 200;

    /**
     * Whether the external storage is mounted
     */
    private boolean mMediaMounted;

    /**
     * Whether the device has a camera
     */
    private boolean mHasCamera;

    /**
     * Uri to the image currently being captured
     */
    private Uri mCapturedPhoto;

    /**
     * The information about each photo
     */
    private ArrayList<PhotoHolder> mData = new ArrayList<>();

    /**
     * The adapter backing the GridView
     */
    private ImageAdapter mAdapter;

    /**
     * Memory cache for bitmaps
     */
    private final BitmapCache mCache = new BitmapCache();

    public AddPhotosFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mMediaMounted = Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
        if(!mMediaMounted) {
            return;
        }
        setHasOptionsMenu(true);

        mHasCamera = getActivity().getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_CAMERA);

        if(savedInstanceState != null) {
            mData = savedInstanceState.getParcelableArrayList(STATE_PHOTOS);
        }

        mAdapter = new ImageAdapter();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if(!mMediaMounted) {
            return inflater.inflate(R.layout.no_media, container, false);
        }

        final View root = inflater.inflate(R.layout.fragment_add_photos, container, false);
        ((GridView)root).setAdapter(mAdapter);
        return root;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelableArrayList(STATE_PHOTOS, mData);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.add_photos_menu, menu);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.findItem(R.id.menu_take_photo).setEnabled(mHasCamera);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_take_photo:
                takePhoto();
                return true;
            case R.id.menu_select_photo:
                addPhotoFromGallery();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Get the photos data as an array of ContentValues objects ready to be bulk inserted into the
     * photos database table.
     *
     * @return Array of ContentValues containing the data for the photos table
     */
    public ContentValues[] getData() {
        final ArrayList<ContentValues> values = new ArrayList<>(mData.size());

        ContentValues rowValues;
        for(PhotoHolder photo : mData) {
            rowValues = new ContentValues();
            rowValues.put(Tables.Photos.PATH, photo.path);

            values.add(rowValues);
        }

        return values.toArray(new ContentValues[values.size()]);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(resultCode == Activity.RESULT_OK) {
            switch(requestCode) {
                case REQUEST_CAPTURE_IMAGE:
                    final Intent scanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                    scanIntent.setData(mCapturedPhoto);
                    getActivity().sendBroadcast(scanIntent);

                    addPhoto(mCapturedPhoto);
                    break;
                case REQUEST_SELECT_IMAGE:
                    if(data != null) {
                        addPhoto(data.getData());
                    }
                    break;
            }
        }
    }

    /**
     * Add a photo to this entry.
     *
     * @param uri The Uri to the image file
     */
    private void addPhoto(Uri uri) {
        final PhotoHolder photo = new PhotoHolder(PhotoUtils.getPath(getActivity(), uri));
        mData.add(photo);
        mAdapter.notifyDataSetChanged();
    }

    /**
     * Launch an image capturing intent.
     */
    private void takePhoto() {
        try {
            mCapturedPhoto = PhotoUtils.getOutputMediaUri();
            final Intent intent = PhotoUtils.getTakePhotoIntent(mCapturedPhoto);
            if(intent.resolveActivity(getActivity().getPackageManager()) != null) {
                getParentFragment().startActivityForResult(intent, REQUEST_CAPTURE_IMAGE);
            }
        } catch(IOException e) {
            Toast.makeText(getActivity(), R.string.error_camera, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Launch an image selection intent.
     */
    private void addPhotoFromGallery() {
        final Intent intent = PhotoUtils.getSelectPhotoIntent();
        getParentFragment().startActivityForResult(intent, REQUEST_SELECT_IMAGE);
    }

    /**
     * Remove the photo at the specified position.
     *
     * @param position The position index of the photo
     */
    private void removePhoto(int position) {
        if(position < 0 || position >= mData.size()) {
            return;
        }
        final PhotoHolder photo = mData.remove(position);
        mCache.remove(photo.path);

        mAdapter.notifyDataSetChanged();
    }

    /**
     * Custom adapter for loading images with remove buttons into the GridView.
     */
    private class ImageAdapter extends BaseAdapter {
        /**
         * List of reusable views
         */
        private final HashMap<String, View> mViews = new HashMap<>();

        /**
         * The size of the cell containing the image
         */
        private final int mFrameSize;

        public ImageAdapter() {
            mFrameSize = getResources().getDimensionPixelSize(R.dimen.photo_grid_size);
        }

        @Override
        public void notifyDataSetChanged() {
            mViews.clear();
            super.notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return mData.size();
        }

        @Override
        public Object getItem(int position) {
            return mData.get(position);
        }

        @Override
        public long getItemId(int position) {
            return mData.get(position).id;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            if(convertView == null) {
                convertView = LayoutInflater.from(getActivity())
                        .inflate(R.layout.photo_grid_item, parent, false);
            }

            final String path = mData.get(position).path;

            if(mViews.containsKey(path)) {
                return mViews.get(path);
            }

            final ImageView imageView = (ImageView)convertView.findViewById(R.id.image);
            loadImage(imageView, path);

            convertView.findViewById(R.id.button_remove_photo)
                    .setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            removePhoto(position);
                        }
                    });

            mViews.put(path, convertView);
            return convertView;
        }

        /**
         * Load a bitmap in the background.
         *
         * @param view The ImageView to hold the bitmap
         * @param path The path to the photo to load
         */
        private void loadImage(ImageView view, String path) {
            final Bitmap bitmap = mCache.get(path);
            if(bitmap != null) {
                view.setImageBitmap(bitmap);
            } else {
                new ImageLoader(view, mFrameSize, mFrameSize, path, mCache).execute();
            }
        }
    }
}
