/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.contacts;

import static com.android.contacts.ContactEntryAdapter.CONTACT_CUSTOM_RINGTONE_COLUMN;
import static com.android.contacts.ContactEntryAdapter.CONTACT_NAME_COLUMN;
import static com.android.contacts.ContactEntryAdapter.CONTACT_PHONETIC_NAME_COLUMN;
import static com.android.contacts.ContactEntryAdapter.CONTACT_PROJECTION;
import static com.android.contacts.ContactEntryAdapter.CONTACT_SEND_TO_VOICEMAIL_COLUMN;
import static com.android.contacts.ContactEntryAdapter.CONTACT_STARRED_COLUMN;
import static com.android.contacts.ContactEntryAdapter.KIND_EMAIL;
import static com.android.contacts.ContactEntryAdapter.KIND_IM;
import static com.android.contacts.ContactEntryAdapter.KIND_ORGANIZATION;
import static com.android.contacts.ContactEntryAdapter.KIND_PHONE;
import static com.android.contacts.ContactEntryAdapter.KIND_POSTAL;
import static com.android.contacts.ContactEntryAdapter.METHODS_AUX_DATA_COLUMN;
import static com.android.contacts.ContactEntryAdapter.METHODS_DATA_COLUMN;
import static com.android.contacts.ContactEntryAdapter.METHODS_ID_COLUMN;
import static com.android.contacts.ContactEntryAdapter.METHODS_KIND_COLUMN;
import static com.android.contacts.ContactEntryAdapter.METHODS_LABEL_COLUMN;
import static com.android.contacts.ContactEntryAdapter.METHODS_STATUS_COLUMN;
import static com.android.contacts.ContactEntryAdapter.METHODS_TYPE_COLUMN;
import static com.android.contacts.ContactEntryAdapter.ORGANIZATIONS_COMPANY_COLUMN;
import static com.android.contacts.ContactEntryAdapter.ORGANIZATIONS_ID_COLUMN;
import static com.android.contacts.ContactEntryAdapter.ORGANIZATIONS_LABEL_COLUMN;
import static com.android.contacts.ContactEntryAdapter.ORGANIZATIONS_PROJECTION;
import static com.android.contacts.ContactEntryAdapter.ORGANIZATIONS_TITLE_COLUMN;
import static com.android.contacts.ContactEntryAdapter.ORGANIZATIONS_TYPE_COLUMN;
import static com.android.contacts.ContactEntryAdapter.PHONES_ID_COLUMN;
import static com.android.contacts.ContactEntryAdapter.PHONES_ISPRIMARY_COLUMN;
import static com.android.contacts.ContactEntryAdapter.PHONES_LABEL_COLUMN;
import static com.android.contacts.ContactEntryAdapter.PHONES_NUMBER_COLUMN;
import static com.android.contacts.ContactEntryAdapter.PHONES_PROJECTION;
import static com.android.contacts.ContactEntryAdapter.PHONES_TYPE_COLUMN;

import com.android.contacts.compat.TelephonyCompat;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Im;
import android.provider.ContactsContract.CommonDataKinds.Organization;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Intents;
import android.provider.ContactsContract.StatusUpdates;
import android.text.TextUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Displays the details of a specific contact.
 */
public class ViewContactActivity extends ListActivity 
        implements View.OnCreateContextMenuListener, View.OnClickListener,
        DialogInterface.OnClickListener {
    private static final String TAG = "ViewContact";
    private static final String SHOW_BARCODE_INTENT = "com.google.zxing.client.android.ENCODE";

    private static final boolean SHOW_SEPARATORS = false;
    
    private static final String[] PHONE_KEYS = {
        Intents.Insert.PHONE,
        Intents.Insert.SECONDARY_PHONE,
        Intents.Insert.TERTIARY_PHONE
    };

    private static final String[] EMAIL_KEYS = {
        Intents.Insert.EMAIL,
        Intents.Insert.SECONDARY_EMAIL,
        Intents.Insert.TERTIARY_EMAIL
    };

    private static final int DIALOG_CONFIRM_DELETE = 1;

    public static final int MENU_ITEM_DELETE = 1;
    public static final int MENU_ITEM_MAKE_DEFAULT = 2;
    public static final int MENU_ITEM_SHOW_BARCODE = 3;
    public static final int MENU_ITEM_EDIT = 4;
    public static final int MENU_ITEM_QUICK_CALL = 5;
    public static final int MENU_ITEM_QUICK_SMS = 6;
    public static final int MENU_ITEM_QUICK_EMAIL = 7;
    public static final int MENU_ITEM_QUICK_ADDRESS = 8;

    private Uri mUri;
    private ContentResolver mResolver;
    private ViewAdapter mAdapter;
    private int mNumPhoneNumbers = 0;

    /* package */ ArrayList<ViewEntry> mPhoneEntries = new ArrayList<ViewEntry>();
    /* package */ ArrayList<ViewEntry> mSmsEntries = new ArrayList<ViewEntry>();
    /* package */ ArrayList<ViewEntry> mEmailEntries = new ArrayList<ViewEntry>();
    /* package */ ArrayList<ViewEntry> mPostalEntries = new ArrayList<ViewEntry>();
    /* package */ ArrayList<ViewEntry> mImEntries = new ArrayList<ViewEntry>();
    /* package */ ArrayList<ViewEntry> mOrganizationEntries = new ArrayList<ViewEntry>();
    /* package */ ArrayList<ViewEntry> mOtherEntries = new ArrayList<ViewEntry>();
    /* package */ ArrayList<ArrayList<ViewEntry>> mSections = new ArrayList<ArrayList<ViewEntry>>();

    private Cursor mCursor;
    private boolean mObserverRegistered;
    
    private ContentObserver mObserver = new ContentObserver(new Handler()) {
        @Override
        public boolean deliverSelfNotifications() {
            return true;
        }

        @Override
        public void onChange(boolean selfChange) {
            if (mCursor != null && !mCursor.isClosed()){
                dataChanged();
            }
        }
    };

    public void onClick(DialogInterface dialog, int which) {
        if (mCursor != null) {
            if (mObserverRegistered) {
                mCursor.unregisterContentObserver(mObserver);
                mObserverRegistered = false;
            }
            mCursor.close();
            mCursor = null;
        }
        getContentResolver().delete(mUri, null, null);
        finish();
    }

    public void onClick(View view) {
        if (!mObserverRegistered) {
            return;
        }
        // switch(view.getId()) nao compila mais (R.id.* deixou de ser "constant
        // expression" com o tema classico como dependencia de biblioteca).
        if (view.getId() == R.id.star) {
            int oldStarredState = mCursor.getInt(CONTACT_STARRED_COLUMN);
            ContentValues values = new ContentValues(1);
            values.put(Contacts.STARRED, oldStarredState == 1 ? 0 : 1);
            getContentResolver().update(mUri, values, null, null);
        }
    }

    private TextView mNameView;
    private TextView mPhoneticNameView;  // may be null in some locales
    private ImageView mPhotoView;
    private int mNoPhotoResource;
    private CheckBox mStarView;
    private boolean mShowSmsLinksForAllPhones;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        setContentView(R.layout.view_contact);
        getListView().setOnCreateContextMenuListener(this);

        mNameView = (TextView) findViewById(R.id.name);
        mPhoneticNameView = (TextView) findViewById(R.id.phonetic_name);
        mPhotoView = (ImageView) findViewById(R.id.photo);
        mStarView = (CheckBox) findViewById(R.id.star);
        mStarView.setOnClickListener(this);

        // Set the photo with a random "no contact" image
        long now = SystemClock.elapsedRealtime();
        int num = (int) now & 0xf;
        if (num < 9) {
            // Leaning in from right, common
            mNoPhotoResource = R.drawable.ic_contact_picture;
        } else if (num < 14) {
            // Leaning in from left uncommon
            mNoPhotoResource = R.drawable.ic_contact_picture_2;
        } else {
            // Coming in from the top, rare
            mNoPhotoResource = R.drawable.ic_contact_picture_3;
        }

        mUri = getIntent().getData();
        mResolver = getContentResolver();

        // Build the list of sections. The order they're added to mSections dictates the
        // order they are displayed in the list.
        mSections.add(mPhoneEntries);
        mSections.add(mSmsEntries);
        mSections.add(mEmailEntries);
        mSections.add(mImEntries);
        mSections.add(mPostalEntries);
        mSections.add(mOrganizationEntries);
        mSections.add(mOtherEntries);

        //TODO Read this value from a preference
        mShowSmsLinksForAllPhones = true;

        mCursor = mResolver.query(mUri, CONTACT_PROJECTION, null, null, null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mObserverRegistered = true;
        mCursor.registerContentObserver(mObserver);
        dataChanged();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mCursor != null) {
            if (mObserverRegistered) {
                mObserverRegistered = false;
                mCursor.unregisterContentObserver(mObserver);
            }
            mCursor.deactivate();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mCursor != null) {
            if (mObserverRegistered) {
                mCursor.unregisterContentObserver(mObserver);
                mObserverRegistered = false;
            }
            mCursor.close();
        }
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case DIALOG_CONFIRM_DELETE:
                return new AlertDialog.Builder(this)
                        .setTitle(R.string.deleteConfirmation_title)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(R.string.deleteConfirmation)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok, this)
                        .setCancelable(false)
                        .create();
        }
        return null;
    }

    private void dataChanged() {
        mCursor.requery();
        if (mCursor.moveToFirst()) {
            // Set the name
            String name = mCursor.getString(CONTACT_NAME_COLUMN);
            if (TextUtils.isEmpty(name)) {
                mNameView.setText(getText(android.R.string.unknownName));
            } else {
                mNameView.setText(name);
            }

            if (mPhoneticNameView != null) {
                String phoneticName = mCursor.getString(CONTACT_PHONETIC_NAME_COLUMN);
                mPhoneticNameView.setText(phoneticName);
            }

            // Load the photo — People.loadContactPhoto(context, uri, fallbackResId, opts)
            // não existe mais; a API pública moderna é um InputStream que a gente decodifica
            // e cai pro drawable padrão se não houver foto.
            Bitmap photoBitmap = null;
            try {
                java.io.InputStream photoStream = Contacts.openContactPhotoInputStream(
                        getContentResolver(), mUri, true);
                if (photoStream != null) {
                    photoBitmap = android.graphics.BitmapFactory.decodeStream(photoStream);
                    photoStream.close();
                }
            } catch (java.io.IOException e) {
                // segue com o drawable padrão
            }
            if (photoBitmap != null) {
                mPhotoView.setImageBitmap(photoBitmap);
            } else {
                mPhotoView.setImageResource(mNoPhotoResource);
            }

            // Set the star
            mStarView.setChecked(mCursor.getInt(CONTACT_STARRED_COLUMN) == 1 ? true : false);

            // Build up the contact entries
            buildEntries(mCursor);
            if (mAdapter == null) {
                mAdapter = new ViewAdapter(this, mSections);
                setListAdapter(mAdapter);
            } else {
                mAdapter.setSections(mSections, SHOW_SEPARATORS);
            }
        } else {
            Toast.makeText(this, R.string.invalidContactMessage, Toast.LENGTH_SHORT).show();
            Log.e(TAG, "invalid contact uri: " + mUri);
            finish();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Corrigido: retirado o .setIntent(...) - ver onOptionsItemSelected.
        menu.add(0, MENU_ITEM_EDIT, 0, R.string.menu_editContact)
                .setIcon(android.R.drawable.ic_menu_edit)
                .setAlphabeticShortcut('e');
        menu.add(0, MENU_ITEM_DELETE, 0, R.string.menu_deleteContact)
                .setIcon(android.R.drawable.ic_menu_delete);

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        // Perform this check each time the menu is about to be shown, because the Barcode Scanner
        // could be installed or uninstalled at any time.
        if (isBarcodeScannerInstalled()) {
            if (menu.findItem(MENU_ITEM_SHOW_BARCODE) == null) {
                menu.add(0, MENU_ITEM_SHOW_BARCODE, 0, R.string.menu_showBarcode)
                        .setIcon(R.drawable.ic_menu_show_barcode);
            }
        } else {
            menu.removeItem(MENU_ITEM_SHOW_BARCODE);
        }
        return true;
    }

    private boolean isBarcodeScannerInstalled() {
        final Intent intent = new Intent(SHOW_BARCODE_INTENT);
        ResolveInfo ri = getPackageManager().resolveActivity(intent,
                PackageManager.MATCH_DEFAULT_ONLY);
        return ri != null;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {
        AdapterView.AdapterContextMenuInfo info;
        try {
             info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        } catch (ClassCastException e) {
            Log.e(TAG, "bad menuInfo", e);
            return;
        }

        // This can be null sometimes, don't crash...
        if (info == null) {
            Log.e(TAG, "bad menuInfo");
            return;
        }

        // Corrigido: nada de .setIntent(...) aqui - ver onContextItemSelected,
        // que reconstrói o "entry" a partir da posição e chama startActivity()
        // direto. É o mesmo crash de FLAG_ACTIVITY_NEW_TASK do menu de opções.
        ViewEntry entry = ContactEntryAdapter.getEntry(mSections, info.position, SHOW_SEPARATORS);
        switch (entry.kind) {
            case KIND_PHONE: {
                menu.add(0, MENU_ITEM_QUICK_CALL, 0, R.string.menu_call);
                menu.add(0, MENU_ITEM_QUICK_SMS, 0, R.string.menu_sendSMS);
                if (entry.primaryIcon == -1) {
                    menu.add(0, MENU_ITEM_MAKE_DEFAULT, 0, R.string.menu_makeDefaultNumber);
                }
                break;
            }

            case KIND_EMAIL: {
                menu.add(0, MENU_ITEM_QUICK_EMAIL, 0, R.string.menu_sendEmail);
                break;
            }

            case KIND_POSTAL: {
                menu.add(0, MENU_ITEM_QUICK_ADDRESS, 0, R.string.menu_viewAddress);
                break;
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_ITEM_EDIT: {
                startActivity(new Intent(Intent.ACTION_EDIT, mUri));
                return true;
            }
            case MENU_ITEM_DELETE: {
                // Get confirmation
                showDialog(DIALOG_CONFIRM_DELETE);
                return true;
            }
            case MENU_ITEM_SHOW_BARCODE:
                if (mCursor.moveToFirst()) {
                    Intent intent = new Intent(SHOW_BARCODE_INTENT);
                    intent.putExtra("ENCODE_TYPE", "CONTACT_TYPE");
                    Bundle bundle = new Bundle();
                    String name = mCursor.getString(CONTACT_NAME_COLUMN);
                    if (!TextUtils.isEmpty(name)) {
                        // Correctly handle when section headers are hidden
                        int sepAdjust = SHOW_SEPARATORS ? 1 : 0;
                        
                        bundle.putString(Intents.Insert.NAME, name);
                        // The 0th ViewEntry in each ArrayList below is a separator item
                        int entriesToAdd = Math.min(mPhoneEntries.size() - sepAdjust, PHONE_KEYS.length);
                        for (int x = 0; x < entriesToAdd; x++) {
                            ViewEntry entry = mPhoneEntries.get(x + sepAdjust);
                            bundle.putString(PHONE_KEYS[x], entry.data);
                        }
                        entriesToAdd = Math.min(mEmailEntries.size() - sepAdjust, EMAIL_KEYS.length);
                        for (int x = 0; x < entriesToAdd; x++) {
                            ViewEntry entry = mEmailEntries.get(x + sepAdjust);
                            bundle.putString(EMAIL_KEYS[x], entry.data);
                        }
                        if (mPostalEntries.size() >= 1 + sepAdjust) {
                            ViewEntry entry = mPostalEntries.get(sepAdjust);
                            bundle.putString(Intents.Insert.POSTAL, entry.data);
                        }
                        intent.putExtra("ENCODE_DATA", bundle);
                        try {
                            startActivity(intent);
                        } catch (ActivityNotFoundException e) {
                            // The check in onPrepareOptionsMenu() should make this impossible, but
                            // for safety I'm catching the exception rather than crashing. Ideally
                            // I'd call Menu.removeItem() here too, but I don't see a way to get
                            // the options menu.
                            Log.e(TAG, "Show barcode menu item was clicked but Barcode Scanner " +
                                    "was not installed.");
                        }
                        return true;
                    }
                }
                break;
        }
        return super.onOptionsItemSelected(item);
    }
    
    // Inicia a Activity e, se não existir nenhum app instalado capaz de tratar
    // o Intent (ex.: ainda não existe um discador padrão instalado - o módulo
    // :phone deste projeto ainda tá sendo construído), avisa em vez de
    // crashar com ActivityNotFoundException.
    private void safeStartActivity(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "nenhum app instalado pra: " + intent, e);
            Toast.makeText(this, R.string.noAppToHandleAction, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info;
        try {
             info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        } catch (ClassCastException e) {
            Log.e(TAG, "bad menuInfo", e);
            return super.onContextItemSelected(item);
        }

        switch (item.getItemId()) {
            case MENU_ITEM_QUICK_CALL: {
                ViewEntry entry = ContactEntryAdapter.getEntry(mSections, info.position,
                        SHOW_SEPARATORS);
                if (entry.intent != null) {
                    safeStartActivity(entry.intent);
                }
                return true;
            }

            case MENU_ITEM_QUICK_SMS: {
                ViewEntry entry = ContactEntryAdapter.getEntry(mSections, info.position,
                        SHOW_SEPARATORS);
                if (entry.auxIntent != null) {
                    safeStartActivity(entry.auxIntent);
                }
                return true;
            }

            case MENU_ITEM_QUICK_EMAIL:
            case MENU_ITEM_QUICK_ADDRESS: {
                ViewEntry entry = ContactEntryAdapter.getEntry(mSections, info.position,
                        SHOW_SEPARATORS);
                if (entry.intent != null) {
                    safeStartActivity(entry.intent);
                }
                return true;
            }

            case MENU_ITEM_MAKE_DEFAULT: {
                ViewEntry entry = ContactEntryAdapter.getEntry(mSections, info.position,
                        SHOW_SEPARATORS);
                // Antes: People.PRIMARY_PHONE_ID na linha do contato (denormalizado).
                // Agora: IS_SUPER_PRIMARY é uma coluna da própria linha de telefone.
                ContentValues values = new ContentValues(1);
                values.put(Phone.IS_SUPER_PRIMARY, 1);
                getContentResolver().update(
                        ContentUris.withAppendedId(Phone.CONTENT_URI, entry.id),
                        values, null, null);
                dataChanged();
                return true;
            }
        }
        return super.onContextItemSelected(item);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_CALL: {
                if (!TelephonyCompat.isIdle(this)) {
                    // Skip out and let the key be handled at a higher level
                    break;
                }

                int index = getListView().getSelectedItemPosition();
                if (index != -1) {
                    ViewEntry entry = ViewAdapter.getEntry(mSections, index, SHOW_SEPARATORS);
                    if (entry.kind == KIND_PHONE) {
                        Intent intent = new Intent(Intent.ACTION_CALL, entry.uri);
                        safeStartActivity(intent);
                    }
                } else if (mNumPhoneNumbers != 0) {
                    // There isn't anything selected, call the default number
                    Intent intent = new Intent(Intent.ACTION_CALL, mUri);
                    safeStartActivity(intent);
                }
                return true;
            }

            case KeyEvent.KEYCODE_DEL: {
                showDialog(DIALOG_CONFIRM_DELETE);
                return true;
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        ViewEntry entry = ViewAdapter.getEntry(mSections, position, SHOW_SEPARATORS);
        if (entry != null) {
            Intent intent = entry.intent;
            if (intent != null) {
                try {
                    startActivity(intent);
                } catch (ActivityNotFoundException e) {
                    Log.e(TAG, "No activity found for intent: " + intent);
                    signalError();
                }
            } else {
                signalError();
            }
        } else {
            signalError();
        }
    }

    /**
     * Signal an error to the user via a beep, or some other method.
     */
    private void signalError() {
        //TODO: implement this when we have the sonification APIs
    }

    /**
     * Build separator entries for all of the sections.
     */
    private void buildSeparators() {
        ViewEntry separator;
        
        separator = new ViewEntry();
        separator.kind = ViewEntry.KIND_SEPARATOR;
        separator.data = getString(R.string.listSeparatorCallNumber);
        mPhoneEntries.add(separator);

        separator = new ViewEntry();
        separator.kind = ViewEntry.KIND_SEPARATOR;
        separator.data = getString(R.string.listSeparatorSendSmsMms);
        mSmsEntries.add(separator);

        separator = new ViewEntry();
        separator.kind = ViewEntry.KIND_SEPARATOR;
        separator.data = getString(R.string.listSeparatorSendEmail);
        mEmailEntries.add(separator);

        separator = new ViewEntry();
        separator.kind = ViewEntry.KIND_SEPARATOR;
        separator.data = getString(R.string.listSeparatorSendIm);
        mImEntries.add(separator);

        separator = new ViewEntry();
        separator.kind = ViewEntry.KIND_SEPARATOR;
        separator.data = getString(R.string.listSeparatorMapAddress);
        mPostalEntries.add(separator);

        separator = new ViewEntry();
        separator.kind = ViewEntry.KIND_SEPARATOR;
        separator.data = getString(R.string.listSeparatorOrganizations);
        mOrganizationEntries.add(separator);

        separator = new ViewEntry();
        separator.kind = ViewEntry.KIND_SEPARATOR;
        separator.data = getString(R.string.listSeparatorOtherInformation);
        mOtherEntries.add(separator);
    }

    private Uri constructImToUrl(String host, String data) {
	    // don't encode the url, because the Activity Manager can't find using the encoded url
        StringBuilder buf = new StringBuilder("imto://");
        buf.append(host);
        buf.append('/');
        buf.append(data);
        return Uri.parse(buf.toString());
    }

    /**
     * Build up the entries to display on the screen.
     * 
     * @param personCursor the URI for the contact being displayed
     */
    private final void buildEntries(Cursor personCursor) {
        // Clear out the old entries
        final int numSections = mSections.size();
        for (int i = 0; i < numSections; i++) {
            mSections.get(i).clear();
        }

        if (SHOW_SEPARATORS) {
            buildSeparators();
        }

        // Build up the phone entries
        final long contactId = ContentUris.parseId(mUri);
        final Cursor phonesCursor = mResolver.query(Phone.CONTENT_URI, PHONES_PROJECTION,
                Phone.CONTACT_ID + "=?", new String[] { String.valueOf(contactId) },
                Phone.IS_PRIMARY + " DESC");

        if (phonesCursor != null) {
            while (phonesCursor.moveToNext()) {
                final int type = phonesCursor.getInt(PHONES_TYPE_COLUMN);
                final String number = phonesCursor.getString(PHONES_NUMBER_COLUMN);
                final String label = phonesCursor.getString(PHONES_LABEL_COLUMN);
                final boolean isPrimary = phonesCursor.getInt(PHONES_ISPRIMARY_COLUMN) == 1;
                final long id = phonesCursor.getLong(PHONES_ID_COLUMN);
                final Uri uri = ContentUris.withAppendedId(Phone.CONTENT_URI, id);

                // Don't crash if the number is bogus
                if (TextUtils.isEmpty(number)) {
                    Log.w(TAG, "empty number for phone " + id);
                    continue;
                }

                mNumPhoneNumbers++;
                
                // Add a phone number entry
                final ViewEntry entry = new ViewEntry();
                final CharSequence displayLabel = Phone.getTypeLabel(getResources(), type, label);
                entry.label = buildActionString(R.string.actionCall, displayLabel, true);
                entry.data = number;
                entry.id = id;
                entry.uri = uri;
                entry.intent = new Intent(Intent.ACTION_CALL, entry.uri);
                entry.auxIntent = new Intent(Intent.ACTION_SENDTO,
                        Uri.fromParts("sms", number, null));
                entry.kind = KIND_PHONE;
                if (isPrimary) {
                    entry.primaryIcon = R.drawable.ic_default_number;
                }
                entry.actionIcon = android.R.drawable.sym_action_call;
                mPhoneEntries.add(entry);

                if (type == Phone.TYPE_MOBILE || mShowSmsLinksForAllPhones) {
                    // Add an SMS entry
                    ViewEntry smsEntry = new ViewEntry();
                    smsEntry.label = buildActionString(R.string.actionText, displayLabel, true);
                    smsEntry.data = number;
                    smsEntry.id = id;
                    smsEntry.uri = uri;
                    smsEntry.intent = entry.auxIntent;
                    smsEntry.kind = ViewEntry.KIND_SMS;
                    smsEntry.actionIcon = R.drawable.sym_action_sms;
                    mSmsEntries.add(smsEntry);
                }
            }

            phonesCursor.close();
        }

        // Build the contact method entries (email, endereço, IM) — antes vinha de um
        // sub-path "contact_methods_with_presence" da URI da pessoa; agora usa o
        // cursor mesclado que reconstrói essa mesma forma a partir de Email/
        // StructuredPostal/Im (tabelas separadas no ContactsContract).
        Cursor methodsCursor = ContactEntryAdapter.queryContactMethods(this, contactId, true);

        if (methodsCursor != null) {
            while (methodsCursor.moveToNext()) {
                final int kind = methodsCursor.getInt(METHODS_KIND_COLUMN);
                final String label = methodsCursor.getString(METHODS_LABEL_COLUMN);
                final String data = methodsCursor.getString(METHODS_DATA_COLUMN);
                final int type = methodsCursor.getInt(METHODS_TYPE_COLUMN);
                final long id = methodsCursor.getLong(METHODS_ID_COLUMN);

                // Don't crash if the data is bogus
                if (TextUtils.isEmpty(data)) {
                    Log.w(TAG, "empty data for contact method " + id);
                    continue;
                }

                ViewEntry entry = new ViewEntry();
                entry.id = id;
                entry.kind = kind;

                switch (kind) {
                    case KIND_EMAIL:
                        entry.uri = ContentUris.withAppendedId(Email.CONTENT_URI, id);
                        entry.label = buildActionString(R.string.actionEmail,
                                Email.getTypeLabel(getResources(), type, label), true);
                        entry.data = data;
                        entry.intent = new Intent(Intent.ACTION_SENDTO,
                                Uri.fromParts("mailto", data, null));
                        entry.actionIcon = android.R.drawable.sym_action_email;
                        mEmailEntries.add(entry);
                        break;

                    case KIND_POSTAL:
                        entry.uri = ContentUris.withAppendedId(StructuredPostal.CONTENT_URI, id);
                        entry.label = buildActionString(R.string.actionMap,
                                StructuredPostal.getTypeLabel(getResources(), type, label), true);
                        entry.data = data;
                        entry.maxLines = 4;
                        entry.intent = new Intent(Intent.ACTION_VIEW, entry.uri);
                        entry.actionIcon = R.drawable.sym_action_map;
                        mPostalEntries.add(entry);
                        break;

                    case KIND_IM: {
                        entry.uri = ContentUris.withAppendedId(Data.CONTENT_URI, id);
                        // aux_data guarda o protocolo (int, era um valor "decodificado" no
                        // formato antigo) — Im.getProtocolLabel já resolve o nome exibível.
                        String auxData = methodsCursor.getString(METHODS_AUX_DATA_COLUMN);
                        String host = null;
                        if (!TextUtils.isEmpty(auxData)) {
                            int protocol = Integer.parseInt(auxData);
                            entry.label = buildActionString(R.string.actionChat,
                                    Im.getProtocolLabel(getResources(), protocol, null), false);
                            if (protocol == Im.PROTOCOL_GOOGLE_TALK
                                    || protocol == Im.PROTOCOL_MSN) {
                                entry.maxLabelLines = 2;
                            }
                            // host pro link im: — mantém simples, usa o label como host
                            host = Im.getProtocolLabel(getResources(), protocol, null)
                                    .toString().toLowerCase();
                        }

                        // Only add the intent if there is a valid host
                        if (!TextUtils.isEmpty(host)) {
                            entry.intent = new Intent(Intent.ACTION_SENDTO,
                                    constructImToUrl(host, data));
                        }
                        entry.data = data;
                        if (!methodsCursor.isNull(METHODS_STATUS_COLUMN)) {
                            entry.presenceIcon = StatusUpdates.getPresenceIconResourceId(
                                    methodsCursor.getInt(METHODS_STATUS_COLUMN));
                        }
                        entry.actionIcon = android.R.drawable.sym_action_chat;
                        mImEntries.add(entry);
                        break;
                    }
                }
            }

            methodsCursor.close();
        }

        // O bloco antigo "presença de buddy sem entrada IM explícita" consultava a
        // tabela Presence separada por PERSON_ID — um registro de presença "solto",
        // sem uma linha de Data (ContactMethods) associada. O ContactsContract moderno
        // não tem mais esse conceito: toda presença (StatusUpdates) é sempre anexada a
        // uma linha de Data específica, e o queryContactMethods() acima já traz essa
        // presença junto (METHODS_STATUS_COLUMN) pra cada linha de IM/Email existente.
        // Removido sem substituto — não há mais "presença órfã" pra reconstruir.

        // Build the organization entries
        // Organization nao tem CONTENT_URI proprio - consulta via Data.CONTENT_URI
        // filtrando por MIMETYPE (mesmo padrao usado em EditContactActivity/VCardExporter).
        Cursor organizationsCursor = mResolver.query(Data.CONTENT_URI,
                ORGANIZATIONS_PROJECTION,
                Organization.CONTACT_ID + "=? AND " + Data.MIMETYPE + "=?",
                new String[] { String.valueOf(contactId), Organization.CONTENT_ITEM_TYPE }, null);

        if (organizationsCursor != null) {
            while (organizationsCursor.moveToNext()) {
                ViewEntry entry = new ViewEntry();
                entry.id = organizationsCursor.getLong(ORGANIZATIONS_ID_COLUMN);
                entry.uri = ContentUris.withAppendedId(Data.CONTENT_URI, entry.id);
                entry.kind = KIND_ORGANIZATION;
                entry.label = organizationsCursor.getString(ORGANIZATIONS_COMPANY_COLUMN);
                entry.data = organizationsCursor.getString(ORGANIZATIONS_TITLE_COLUMN);
                entry.actionIcon = R.drawable.sym_action_organization;
                mOrganizationEntries.add(entry);
            }

            organizationsCursor.close();
        }


        // Build the other entries
        String note = ContactEntryAdapter.getContactNote(this, contactId);
        if (!TextUtils.isEmpty(note)) {
            ViewEntry entry = new ViewEntry();
            entry.label = getString(R.string.label_notes);
            entry.data = note;
            entry.id = 0;
            entry.kind = ViewEntry.KIND_CONTACT;
            entry.uri = null;
            entry.intent = null;
            entry.maxLines = 10;
            entry.actionIcon = R.drawable.sym_note;
            mOtherEntries.add(entry);
        }
        
        // Build the ringtone entry
        String ringtoneStr = personCursor.getString(CONTACT_CUSTOM_RINGTONE_COLUMN);
        if (!TextUtils.isEmpty(ringtoneStr)) {
            // Get the URI
            Uri ringtoneUri = Uri.parse(ringtoneStr);
            if (ringtoneUri != null) {
                Ringtone ringtone = RingtoneManager.getRingtone(this, ringtoneUri);
                if (ringtone != null) {
                    ViewEntry entry = new ViewEntry();
                    entry.label = getString(R.string.label_ringtone);
                    entry.data = ringtone.getTitle(this);
                    entry.kind = ViewEntry.KIND_CONTACT;
                    entry.uri = ringtoneUri;
                    entry.actionIcon = R.drawable.sym_ringtone;
                    mOtherEntries.add(entry);
                }
            }
        }

        // Build the send directly to voice mail entry
        boolean sendToVoicemail = personCursor.getInt(CONTACT_SEND_TO_VOICEMAIL_COLUMN) == 1;
        if (sendToVoicemail) {
            ViewEntry entry = new ViewEntry();
            entry.label = getString(R.string.actionIncomingCall);
            entry.data = getString(R.string.detailIncomingCallsGoToVoicemail);
            entry.kind = ViewEntry.KIND_CONTACT;
            entry.actionIcon = R.drawable.sym_send_to_voicemail;
            mOtherEntries.add(entry);
        }
    }

    String buildActionString(int actionResId, CharSequence type, boolean lowerCase) {
        // If there is no type just display an empty string
        if (type == null) {
            type = "";
        }

        if (lowerCase) {
            return getString(actionResId, type.toString().toLowerCase());
        } else {
            return getString(actionResId, type.toString());
        }
    }
    
    /**
     * A basic structure with the data for a contact entry in the list.
     */
    final static class ViewEntry extends ContactEntryAdapter.Entry {
        public int primaryIcon = -1;
        public Intent intent;
        public Intent auxIntent = null;
        public int presenceIcon = -1;
        public int actionIcon = -1;
        public int maxLabelLines = 1;
    }

    private static final class ViewAdapter extends ContactEntryAdapter<ViewEntry> {
        /** Cache of the children views of a row */
        static class ViewCache {
            public TextView label;
            public TextView data;
            public ImageView actionIcon;
            public ImageView presenceIcon;
            
            // Need to keep track of this too
            ViewEntry entry;
        }
        
        ViewAdapter(Context context, ArrayList<ArrayList<ViewEntry>> sections) {
            super(context, sections, SHOW_SEPARATORS);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewEntry entry = getEntry(mSections, position, false); 
            View v;

            // Handle separators specially
            if (entry.kind == ViewEntry.KIND_SEPARATOR) {
                TextView separator = (TextView) mInflater.inflate(
                        R.layout.list_separator, parent, SHOW_SEPARATORS);
                separator.setText(entry.data);
                return separator;
            }

            ViewCache views;

            // Check to see if we can reuse convertView
            if (convertView != null) {
                v = convertView;
                views = (ViewCache) v.getTag();
            } else {
                // Create a new view if needed
                v = mInflater.inflate(R.layout.list_item_text_icons, parent, false);

                // Cache the children
                views = new ViewCache();
                views.label = (TextView) v.findViewById(android.R.id.text1);
                views.data = (TextView) v.findViewById(android.R.id.text2);
                views.actionIcon = (ImageView) v.findViewById(R.id.icon1);
                views.presenceIcon = (ImageView) v.findViewById(R.id.icon2);
                v.setTag(views);
            }

            // Update the entry in the view cache
            views.entry = entry;

            // Bind the data to the view
            bindView(v, entry);
            return v;
        }

        @Override
        protected View newView(int position, ViewGroup parent) {
            // getView() handles this
            throw new UnsupportedOperationException();
        }

        @Override
        protected void bindView(View view, ViewEntry entry) {
            final Resources resources = mContext.getResources();
            ViewCache views = (ViewCache) view.getTag();

            // Set the label
            TextView label = views.label;
            setMaxLines(label, entry.maxLabelLines);
            label.setText(entry.label);

            // Set the data
            TextView data = views.data;
            if (data != null) {
                data.setText(entry.data);
                setMaxLines(data, entry.maxLines);
            }

            // Set the action icon
            ImageView action = views.actionIcon;
            if (entry.actionIcon != -1) {
                action.setImageDrawable(resources.getDrawable(entry.actionIcon));
                action.setVisibility(View.VISIBLE);
            } else {
                // Things should still line up as if there was an icon, so make it invisible
                action.setVisibility(View.INVISIBLE);
            }

            // Set the presence icon
            Drawable presenceIcon = null;
            if (entry.primaryIcon != -1) {
                presenceIcon = resources.getDrawable(entry.primaryIcon);
            } else if (entry.presenceIcon != -1) {
                presenceIcon = resources.getDrawable(entry.presenceIcon);
            }

            ImageView presence = views.presenceIcon;
            if (presenceIcon != null) {
                presence.setImageDrawable(presenceIcon);
                presence.setVisibility(View.VISIBLE);
            } else {
                presence.setVisibility(View.GONE);
            }
        }

        private void setMaxLines(TextView textView, int maxLines) {
            if (maxLines == 1) {
                textView.setSingleLine(true);
                textView.setEllipsize(TextUtils.TruncateAt.END);
            } else {
                textView.setSingleLine(false);
                textView.setMaxLines(maxLines);
                textView.setEllipsize(null);
            }
        }
    }
}
