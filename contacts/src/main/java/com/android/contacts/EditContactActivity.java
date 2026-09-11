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
import static com.android.contacts.ContactEntryAdapter.CONTACT_PROJECTION;
import static com.android.contacts.ContactEntryAdapter.CONTACT_SEND_TO_VOICEMAIL_COLUMN;
import static com.android.contacts.ContactEntryAdapter.CONTACT_PHONETIC_NAME_COLUMN;
import static com.android.contacts.ContactEntryAdapter.KIND_EMAIL;
import static com.android.contacts.ContactEntryAdapter.KIND_IM;
import static com.android.contacts.ContactEntryAdapter.KIND_ORGANIZATION;
import static com.android.contacts.ContactEntryAdapter.KIND_PHONE;
import static com.android.contacts.ContactEntryAdapter.KIND_POSTAL;
import static com.android.contacts.ContactEntryAdapter.METHODS_AUX_DATA_COLUMN;
import static com.android.contacts.ContactEntryAdapter.METHODS_DATA_COLUMN;
import static com.android.contacts.ContactEntryAdapter.METHODS_ID_COLUMN;
import static com.android.contacts.ContactEntryAdapter.METHODS_ISPRIMARY_COLUMN;
import static com.android.contacts.ContactEntryAdapter.METHODS_KIND_COLUMN;
import static com.android.contacts.ContactEntryAdapter.METHODS_LABEL_COLUMN;
import static com.android.contacts.ContactEntryAdapter.METHODS_PROJECTION;
import static com.android.contacts.ContactEntryAdapter.METHODS_TYPE_COLUMN;
import static com.android.contacts.ContactEntryAdapter.ORGANIZATIONS_COMPANY_COLUMN;
import static com.android.contacts.ContactEntryAdapter.ORGANIZATIONS_ID_COLUMN;
import static com.android.contacts.ContactEntryAdapter.ORGANIZATIONS_ISPRIMARY_COLUMN;
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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Im;
import android.provider.ContactsContract.CommonDataKinds.Note;
import android.provider.ContactsContract.CommonDataKinds.Organization;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Intents.Insert;
import android.provider.ContactsContract.RawContacts;
import android.telephony.PhoneNumberFormattingTextWatcher;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.TextKeyListener;
import android.text.method.TextKeyListener.Capitalize;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;

/**
 * Activity for editing or inserting a contact. Note that if the contact data changes in the
 * background while this activity is running, the updates will be overwritten.
 */
public final class EditContactActivity extends Activity implements View.OnClickListener,
        TextWatcher, View.OnFocusChangeListener {
    private static final String TAG = "EditContactActivity";

    private static final int STATE_UNKNOWN = 0;
    /** Editing an existing contact */
    private static final int STATE_EDIT = 1;
    /** The full insert mode */
    private static final int STATE_INSERT = 2;

    /** The launch code when picking a photo and the raw data is returned */
    private static final int PHOTO_PICKED_WITH_DATA = 3021;

    /** The launch code when picking a ringtone */
    private static final int RINGTONE_PICKED = 3023;
    
    // These correspond to the string array in resources for picker "other" items
    final static int OTHER_ORGANIZATION = 0;
    final static int OTHER_NOTE = 1;
    
    // Dialog IDs
    final static int DELETE_CONFIRMATION_DIALOG = 2;
    
    // Section IDs
    final static int SECTION_PHONES = 3;
    final static int SECTION_EMAIL = 4;
    final static int SECTION_IM = 5;
    final static int SECTION_POSTAL = 6;
    final static int SECTION_ORG = 7;
    final static int SECTION_NOTE = 8;

    // Menu item IDs
    public static final int MENU_ITEM_SAVE = 1;
    public static final int MENU_ITEM_DONT_SAVE = 2;
    public static final int MENU_ITEM_DELETE = 3;
    public static final int MENU_ITEM_PHOTO = 6;
    
    /** Used to represent an invalid type for a contact entry */
    private static final int INVALID_TYPE = -1;
    
    /** The default type for a phone that is added via an intent */
    private static final int DEFAULT_PHONE_TYPE = Phone.TYPE_MOBILE;

    /** The default type for an email that is added via an intent */
    private static final int DEFAULT_EMAIL_TYPE = Email.TYPE_HOME;

    /** The default type for a postal address that is added via an intent */
    private static final int DEFAULT_POSTAL_TYPE = Email.TYPE_HOME;

    private int mState; // saved across instances
    private boolean mInsert; // saved across instances
    private Uri mUri; // saved across instances
    /** In insert mode this is the photo */
    private Bitmap mPhoto; // saved across instances
    private boolean mPhotoChanged = false; // saved across instances
    
    private EditText mNameView;
    private ImageView mPhotoImageView;
    private ViewGroup mContentView;
    private LinearLayout mLayout;
    private LayoutInflater mInflater;
    private MenuItem mPhotoMenuItem;
    private boolean mPhotoPresent = false;
    private EditText mPhoneticNameView;  // invisible in some locales, but always present

    /** Flag marking this contact as changed, meaning we should write changes back. */
    private boolean mContactChanged = false;

    // These are accessed by inner classes. They're package scoped to make access more efficient.
    /* package */ ContentResolver mResolver;
    /* package */ ArrayList<EditEntry> mPhoneEntries = new ArrayList<EditEntry>();
    /* package */ ArrayList<EditEntry> mEmailEntries = new ArrayList<EditEntry>();
    /* package */ ArrayList<EditEntry> mImEntries = new ArrayList<EditEntry>();
    /* package */ ArrayList<EditEntry> mPostalEntries = new ArrayList<EditEntry>();
    /* package */ ArrayList<EditEntry> mOrgEntries = new ArrayList<EditEntry>();
    /* package */ ArrayList<EditEntry> mNoteEntries = new ArrayList<EditEntry>();
    /* package */ ArrayList<EditEntry> mOtherEntries = new ArrayList<EditEntry>();
    /* package */ ArrayList<ArrayList<EditEntry>> mSections = new ArrayList<ArrayList<EditEntry>>();
    
    /* package */ static final int MSG_DELETE = 1;
    /* package */ static final int MSG_CHANGE_LABEL = 2;
    /* package */ static final int MSG_ADD_PHONE = 3;
    /* package */ static final int MSG_ADD_EMAIL = 4;
    /* package */ static final int MSG_ADD_POSTAL = 5;

    private static final int[] TYPE_PRECEDENCE_PHONES = new int[] {
            Phone.TYPE_MOBILE, Phone.TYPE_HOME, Phone.TYPE_WORK, Phone.TYPE_OTHER
    };
    private static final int[] TYPE_PRECEDENCE_METHODS = new int[] {
            Email.TYPE_HOME, Email.TYPE_WORK, Email.TYPE_OTHER
    };
    private static final int[] TYPE_PRECEDENCE_IM = new int[] {
            Im.PROTOCOL_GOOGLE_TALK, Im.PROTOCOL_AIM,
            Im.PROTOCOL_MSN, Im.PROTOCOL_YAHOO,
            Im.PROTOCOL_JABBER
    };
    private static final int[] TYPE_PRECEDENCE_ORG = new int[] {
            Organization.TYPE_WORK, Organization.TYPE_OTHER
    };

    public void onClick(View v) {
        // switch(v.getId()) nao compila mais aqui: com o modulo de tema classico
        // virando dependencia de biblioteca, os R.id.* deixam de ser "constant
        // expression" em tempo de compilacao. Convertido pra if/else if.
        int clickedId = v.getId();
        if (clickedId == R.id.photoImage) {
            doPickPhotoAction();

        } else if (clickedId == R.id.checkable) {
            CheckBox checkBox = (CheckBox) v.findViewById(R.id.checkbox);
            checkBox.toggle();

            EditEntry entry = findEntryForView(v);
            entry.data = checkBox.isChecked() ? "1" : "0";

            mContactChanged = true;

        } else if (clickedId == R.id.entry_ringtone) {
            EditEntry entry = findEntryForView(v);
            doPickRingtone(entry);

        } else if (clickedId == R.id.separator) {
            // Someone clicked on a section header, so handle add action
            int sectionType = (Integer) v.getTag();
            doAddAction(sectionType);

        } else if (clickedId == R.id.saveButton) {
            doSaveAction();

        } else if (clickedId == R.id.discardButton) {
            doRevertAction();

        } else if (clickedId == R.id.delete) {
            EditEntry entry = findEntryForView(v);
            if (entry != null) {
                // Clear the text and hide the view so it gets saved properly
                ((TextView) entry.view.findViewById(R.id.data)).setText(null);
                entry.view.setVisibility(View.GONE);
                entry.isDeleted = true;
            }

            // Force rebuild of views because section headers might need to change
            buildViews();

        } else if (clickedId == R.id.label) {
            EditEntry entry = findEntryForView(v);
            if (entry != null) {
                String[] labels = getLabelsForKind(this, entry.kind);
                LabelPickedListener listener = new LabelPickedListener(entry, labels);
                new AlertDialog.Builder(EditContactActivity.this)
                        .setItems(labels, listener)
                        .setTitle(R.string.selectLabel)
                        .show();
            }
        }
    }

    private void setPhotoPresent(boolean present) {
        mPhotoPresent = present;
        
        // Correctly scale the contact photo if present, otherwise just center
        // the photo placeholder icon.
        if (mPhotoPresent) {
            mPhotoImageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        } else {
            mPhotoImageView.setImageResource(R.drawable.ic_menu_add_picture);
            mPhotoImageView.setScaleType(ImageView.ScaleType.CENTER);
        }
        
        if (mPhotoMenuItem != null) {
            if (present) {
                mPhotoMenuItem.setTitle(R.string.removePicture);
                mPhotoMenuItem.setIcon(android.R.drawable.ic_menu_delete);
            } else {
                mPhotoMenuItem.setTitle(R.string.addPicture);
                mPhotoMenuItem.setIcon(R.drawable.ic_menu_add_picture);
            }
        }
    }
    
    private EditEntry findEntryForView(View v) {
        // Try to find the entry for this view
        EditEntry entry = null;
        do {
            Object tag = v.getTag();
            if (tag != null && tag instanceof EditEntry) {
                entry = (EditEntry) tag;
                break;
            } else {
                ViewParent parent = v.getParent();
                if (parent != null && parent instanceof View) {
                    v = (View) parent;
                } else {
                    v = null;
                }
            }
        } while (v != null);
        return entry;
    }

    private DialogInterface.OnClickListener mDeleteContactDialogListener =
            new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int button) {
            mResolver.delete(mUri, null, null);
            finish();
        }
    };

    private boolean mMobilePhoneAdded = false;
    private boolean mPrimaryEmailAdded = false;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        mResolver = getContentResolver();

        // Build the list of sections
        setupSections();

        // Load the UI
        mInflater = getLayoutInflater();
        mContentView = (ViewGroup)mInflater.inflate(R.layout.edit_contact, null);
        setContentView(mContentView);
        
        mLayout = (LinearLayout) findViewById(R.id.list);
        mNameView = (EditText) findViewById(R.id.name);
        mPhotoImageView = (ImageView) findViewById(R.id.photoImage);
        mPhotoImageView.setOnClickListener(this);
        mPhoneticNameView = (EditText) findViewById(R.id.phonetic_name);
        
        // Setup the bottom buttons
        View view = findViewById(R.id.saveButton);
        view.setOnClickListener(this);
        view = findViewById(R.id.discardButton);
        view.setOnClickListener(this);

        // Resolve the intent
        mState = STATE_UNKNOWN;
        Intent intent = getIntent();
        String action = intent.getAction();
        mUri = intent.getData();
        if (mUri != null) {
            if (action.equals(Intent.ACTION_EDIT)) {
                if (icicle == null) {
                    // Build the entries & views
                    buildEntriesForEdit(getIntent().getExtras());
                    buildViews();
                }
                setTitle(R.string.editContact_title_edit);
                mState = STATE_EDIT;
            } else if (action.equals(Intent.ACTION_INSERT)) {
                if (icicle == null) {
                    // Build the entries & views
                    buildEntriesForInsert(getIntent().getExtras());
                    buildViews();
                }
                setTitle(R.string.editContact_title_insert);
                mState = STATE_INSERT;
                mInsert = true;
            }
        }

        if (mState == STATE_UNKNOWN) {
            Log.e(TAG, "Cannot resolve intent: " + intent);
            finish();
            return;
        }

        if (mState == STATE_EDIT) {
            setTitle(getResources().getText(R.string.editContact_title_edit));
        } else {
            setTitle(getResources().getText(R.string.editContact_title_insert));
        }
    }

    private void setupSections() {
        mSections.add(mPhoneEntries);
        mSections.add(mEmailEntries);
        mSections.add(mImEntries);
        mSections.add(mPostalEntries);
        mSections.add(mOrgEntries);
        mSections.add(mNoteEntries);
        mSections.add(mOtherEntries);
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        
        // To store current focus between config changes, follow focus down the
        // view tree, keeping track of any parents with EditEntry tags
        View focusedChild = mContentView.getFocusedChild();
        EditEntry focusedEntry = null;
        while (focusedChild != null) {
            Object tag = focusedChild.getTag();
            if (tag instanceof EditEntry) {
                focusedEntry = (EditEntry) tag;
            }
            
            // Keep going deeper until child isn't a group
            if (focusedChild instanceof ViewGroup) {
                View deeperFocus = ((ViewGroup) focusedChild).getFocusedChild();
                if (deeperFocus != null) {
                    focusedChild = deeperFocus;
                } else {
                    break;
                }
            } else {
                break;
            }
        }
        
        if (focusedChild != null) {
            int requestFocusId = focusedChild.getId();
            int requestCursor = 0;
            if (focusedChild instanceof EditText) {
                requestCursor = ((EditText) focusedChild).getSelectionStart();
            }
            
            // Store focus values in EditEntry if found, otherwise store as
            // generic values
            if (focusedEntry != null) {
                focusedEntry.requestFocusId = requestFocusId;
                focusedEntry.requestCursor = requestCursor;
            } else {
                outState.putInt("requestFocusId", requestFocusId);
                outState.putInt("requestCursor", requestCursor);
            }
        }
        
        outState.putParcelableArrayList("phoneEntries", mPhoneEntries);
        outState.putParcelableArrayList("emailEntries", mEmailEntries);
        outState.putParcelableArrayList("imEntries", mImEntries);
        outState.putParcelableArrayList("postalEntries", mPostalEntries);
        outState.putParcelableArrayList("orgEntries", mOrgEntries);
        outState.putParcelableArrayList("noteEntries", mNoteEntries);
        outState.putParcelableArrayList("otherEntries", mOtherEntries);
        outState.putInt("state", mState);
        outState.putBoolean("insert", mInsert);
        outState.putParcelable("uri", mUri);
        outState.putString("name", mNameView.getText().toString());
        outState.putParcelable("photo", mPhoto);
        outState.putBoolean("photoChanged", mPhotoChanged);
        outState.putString("phoneticName", mPhoneticNameView.getText().toString());
        outState.putBoolean("contactChanged", mContactChanged);
    }

    @Override
    protected void onRestoreInstanceState(Bundle inState) {
        mPhoneEntries = inState.getParcelableArrayList("phoneEntries");
        mEmailEntries = inState.getParcelableArrayList("emailEntries");
        mImEntries = inState.getParcelableArrayList("imEntries");
        mPostalEntries = inState.getParcelableArrayList("postalEntries");
        mOrgEntries = inState.getParcelableArrayList("orgEntries");
        mNoteEntries = inState.getParcelableArrayList("noteEntries");
        mOtherEntries = inState.getParcelableArrayList("otherEntries");
        setupSections();

        mState = inState.getInt("state");
        mInsert = inState.getBoolean("insert");
        mUri = inState.getParcelable("uri");
        mNameView.setText(inState.getString("name"));
        mPhoto = inState.getParcelable("photo");
        if (mPhoto != null) {
            mPhotoImageView.setImageBitmap(mPhoto);
            setPhotoPresent(true);
        } else {
            mPhotoImageView.setImageResource(R.drawable.ic_contact_picture);
            setPhotoPresent(false);
        }
        mPhotoChanged = inState.getBoolean("photoChanged");
        mPhoneticNameView.setText(inState.getString("phoneticName"));
        mContactChanged = inState.getBoolean("contactChanged");

        // Now that everything is restored, build the view
        buildViews();
        
        // Try restoring any generally requested focus
        int requestFocusId = inState.getInt("requestFocusId", View.NO_ID);
        View focusedChild = mContentView.findViewById(requestFocusId);
        if (focusedChild != null) {
            focusedChild.requestFocus();
            if (focusedChild instanceof EditText) {
                int requestCursor = inState.getInt("requestCursor", 0);
                ((EditText) focusedChild).setSelection(requestCursor);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != RESULT_OK) {
            return;
        }

        switch (requestCode) {
            case PHOTO_PICKED_WITH_DATA: {
                // Corrigido: o seletor de mídia moderno não devolve mais o Bitmap
                // recortado pelo extra "data" - só o Uri da imagem escolhida via
                // data.getData(). Lemos a imagem desse Uri e fazemos o recorte
                // quadrado (centralizado) na mão, no lugar da Activity de CROP
                // antiga que não é mais chamada.
                Bitmap photo = null;
                final Bundle extras = data.getExtras();
                if (extras != null && extras.getParcelable("data") != null) {
                    // Alguns apps antigos ainda devolvem o Bitmap direto - mantido
                    // como caminho de compatibilidade.
                    photo = extras.getParcelable("data");
                } else {
                    Uri pickedImageUri = data.getData();
                    if (pickedImageUri != null) {
                        try {
                            java.io.InputStream imageStream =
                                    getContentResolver().openInputStream(pickedImageUri);
                            Bitmap fullPhoto = android.graphics.BitmapFactory.decodeStream(imageStream);
                            if (imageStream != null) {
                                imageStream.close();
                            }
                            if (fullPhoto != null) {
                                photo = cropToSquare(fullPhoto);
                            }
                        } catch (java.io.IOException e) {
                            Log.e(TAG, "Falha ao ler a imagem escolhida: " + pickedImageUri, e);
                        }
                    }
                }

                if (photo != null) {
                    mPhoto = photo;
                    mPhotoChanged = true;
                    mPhotoImageView.setImageBitmap(photo);
                    setPhotoPresent(true);
                } else {
                    Toast.makeText(this, R.string.photoPickerNotFoundText, Toast.LENGTH_SHORT)
                            .show();
                }
                break;
            }

            case RINGTONE_PICKED: {
                Uri pickedUri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                handleRingtonePicked(pickedUri);
                mContactChanged = true;
                break;
            }
        }
    }
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK: {
                doSaveAction();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, MENU_ITEM_SAVE, 0, R.string.menu_done)
                .setIcon(android.R.drawable.ic_menu_save)
                .setAlphabeticShortcut('\n');
        menu.add(0, MENU_ITEM_DONT_SAVE, 0, R.string.menu_doNotSave)
                .setIcon(android.R.drawable.ic_menu_close_clear_cancel)
                .setAlphabeticShortcut('q');
        if (!mInsert) {
            menu.add(0, MENU_ITEM_DELETE, 0, R.string.menu_deleteContact)
                    .setIcon(android.R.drawable.ic_menu_delete);
        }

        mPhotoMenuItem = menu.add(0, MENU_ITEM_PHOTO, 0, null);
        // Updates the state of the menu item
        setPhotoPresent(mPhotoPresent);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_ITEM_SAVE:
                doSaveAction();
                return true;
    
            case MENU_ITEM_DONT_SAVE:
                doRevertAction();
                return true;
    
            case MENU_ITEM_DELETE:
                // Get confirmation
                showDialog(DELETE_CONFIRMATION_DIALOG);
                return true;
    
            case MENU_ITEM_PHOTO:
                if (!mPhotoPresent) {
                    doPickPhotoAction();
                } else {
                    doRemovePhotoAction();
                }
                return true;
        }

        return false;
    }
    
    /**
     * Try guessing the next-best type of {@link EditEntry} to insert into the
     * given list. We walk down the precedence list until we find a type that
     * doesn't exist yet, or default to the lowest ranking type.
     */
    private int guessNextType(ArrayList<EditEntry> entries, int[] precedenceList) {
        // Keep track of the types we've seen already
        SparseBooleanArray existAlready = new SparseBooleanArray(entries.size());
        for (int i = entries.size() - 1; i >= 0; i--) {
            EditEntry entry = entries.get(i);
            if (!entry.isDeleted) {
                existAlready.put(entry.type, true);
            }
        }
        
        // Pick the first item we haven't seen
        for (int type : precedenceList) {
            if (!existAlready.get(type, false)) {
                return type;
            }
        }
        
        // Otherwise default to last item
        return precedenceList[precedenceList.length - 1];
    }

    private void doAddAction(int sectionType) {
        EditEntry entry = null;
        switch (sectionType) {
            case SECTION_PHONES: {
                // Try figuring out which type to insert next
                int nextType = guessNextType(mPhoneEntries, TYPE_PRECEDENCE_PHONES);
                entry = EditEntry.newPhoneEntry(EditContactActivity.this,
                        Phone.CONTENT_URI, nextType);
                mPhoneEntries.add(entry);
                break;
            }
            case SECTION_EMAIL: {
                // Try figuring out which type to insert next
                int nextType = guessNextType(mEmailEntries, TYPE_PRECEDENCE_METHODS);
                entry = EditEntry.newEmailEntry(EditContactActivity.this,
                        Email.CONTENT_URI, nextType);
                mEmailEntries.add(entry);
                break;
            }
            case SECTION_IM: {
                // Try figuring out which type to insert next
                int nextType = guessNextType(mImEntries, TYPE_PRECEDENCE_IM);
                entry = EditEntry.newImEntry(EditContactActivity.this,
                        Data.CONTENT_URI, nextType);
                mImEntries.add(entry);
                break;
            }
            case SECTION_POSTAL: {
                int nextType = guessNextType(mPostalEntries, TYPE_PRECEDENCE_METHODS);
                entry = EditEntry.newPostalEntry(EditContactActivity.this,
                        StructuredPostal.CONTENT_URI, nextType);
                mPostalEntries.add(entry);
                break;
            }
            case SECTION_ORG: {
                int nextType = guessNextType(mOrgEntries, TYPE_PRECEDENCE_ORG);
                entry = EditEntry.newOrganizationEntry(EditContactActivity.this,
                        Data.CONTENT_URI, nextType);
                mOrgEntries.add(entry);
                break;
            }
            case SECTION_NOTE: {
                entry = EditEntry.newNotesEntry(EditContactActivity.this, null, null);
                mNoteEntries.add(entry);
                break;
            }
        }
        
        // Rebuild the views if needed
        if (entry != null) {
            buildViews();
            mContactChanged = true;

            View dataView = entry.view.findViewById(R.id.data);
            if (dataView == null) {
                entry.view.requestFocus();
            } else {
                dataView.requestFocus();
            }
        }
    }

    private void doRevertAction() {
        finish();
    }

    /**
     * Recorta um bitmap para um quadrado centralizado, pra substituir o recorte
     * que antes era feito pela Activity de CROP externa (ver doPickPhotoAction).
     */
    private Bitmap cropToSquare(Bitmap source) {
        int width = source.getWidth();
        int height = source.getHeight();
        int size = Math.min(width, height);
        int x = (width - size) / 2;
        int y = (height - size) / 2;
        return Bitmap.createBitmap(source, x, y, size, size);
    }

    private void doPickPhotoAction() {
        // Corrigido: os extras "crop"/"return-data" dependiam de uma Activity de
        // recorte (com.android.camera.action.CROP) que os seletores de mídia
        // modernos (Photo Picker, apps de galeria atuais) ignoram - eles nunca
        // devolvem o Bitmap pelo extra "data", só o Uri da imagem via
        // Intent.getData(). Por isso a foto nunca aparecia (ficava só o fallback
        // genérico). O recorte quadrado agora é feito manualmente em
        // onActivityResult, a partir do Uri.
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT, null);
        intent.setType("image/*");
        try {
            startActivityForResult(intent, PHOTO_PICKED_WITH_DATA);
        } catch (ActivityNotFoundException e) {
            new AlertDialog.Builder(EditContactActivity.this)
                .setTitle(R.string.errorDialogTitle)
                .setMessage(R.string.photoPickerNotFoundText)
                .setPositiveButton(android.R.string.ok, null)
                .show();
        }
    }

    private void doRemovePhotoAction() {
        mPhoto = null;
        mPhotoChanged = true;
        setPhotoPresent(false);
    }
    
    private void doPickRingtone(EditEntry entry) {
        Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        // Allow user to pick 'Default'
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
        // Show only ringtones
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE);
        // Don't show 'Silent'
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
        
        Uri ringtoneUri;
        if (entry.data != null) {
            ringtoneUri = Uri.parse(entry.data);
        } else {
            // Otherwise pick default ringtone Uri so that something is selected.
            ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        }
        
        // Put checkmark next to the current ringtone for this contact
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, ringtoneUri);
        // Launch!
        startActivityForResult(intent, RINGTONE_PICKED);
    }
    
    private void handleRingtonePicked(Uri pickedUri) {
        EditEntry entry = getOtherEntry(Contacts.CUSTOM_RINGTONE);
        if (entry == null) {
            Log.w(TAG, "Ringtone picked but could not find ringtone entry");
            return;
        }
        
        if (pickedUri == null || RingtoneManager.isDefault(pickedUri)) {
            entry.data = null;
        } else {
            entry.data = pickedUri.toString();
        }
        
        updateRingtoneView(entry);
    }

    private void updateRingtoneView(EditEntry entry) {
        String ringtoneName;
        if (entry.data == null) {
            ringtoneName = getString(R.string.default_ringtone);
        } else {
            Uri ringtoneUri = Uri.parse(entry.data);
            Ringtone ringtone = RingtoneManager.getRingtone(this, ringtoneUri);
            if (ringtone == null) {
                Log.w(TAG, "ringtone's URI doesn't resolve to a Ringtone");
                return;
            }
            ringtoneName = ringtone.getTitle(this);
        }
        
        updateDataView(entry, ringtoneName);
    }
    
    private void updateDataView(EditEntry entry, String text) {
        TextView dataView = (TextView) entry.view.findViewById(R.id.data);
        dataView.setText(text);
    }
    
    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case DELETE_CONFIRMATION_DIALOG:
                return new AlertDialog.Builder(EditContactActivity.this)
                        .setTitle(R.string.deleteConfirmation_title)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(R.string.deleteConfirmation)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok, mDeleteContactDialogListener)
                        .setCancelable(false)
                        .create();
        }
        return super.onCreateDialog(id);
    }
    
    static String[] getLabelsForKind(Context context, int kind) {
        final Resources resources = context.getResources();
        switch (kind) {
            case KIND_PHONE:
                return resources.getStringArray(android.R.array.phoneTypes);
            case KIND_EMAIL:
                return resources.getStringArray(android.R.array.emailAddressTypes);
            case KIND_POSTAL:
                return resources.getStringArray(android.R.array.postalAddressTypes);
            case KIND_IM:
                return resources.getStringArray(android.R.array.imProtocols);
            case KIND_ORGANIZATION:
                return resources.getStringArray(android.R.array.organizationTypes);
            case EditEntry.KIND_CONTACT:
                return resources.getStringArray(R.array.otherLabels);
        }
        return null;
    }

    int getTypeFromLabelPosition(CharSequence[] labels, int labelPosition) {
        // In the UI Custom... comes last, but it is uses the constant 0
        // so it is in the same location across the various kinds. Fix up the
        // position to a valid type here.
        if (labelPosition == labels.length - 1) {
            return Email.TYPE_CUSTOM;
        } else {
            return labelPosition + 1;
        }
    }
    
    private EditEntry getOtherEntry(String column) {
        for (int i = mOtherEntries.size() - 1; i >= 0; i--) {
            EditEntry entry = mOtherEntries.get(i);
            if (isOtherEntry(entry, column)) {
                return entry;
            }
        }
        return null;
    }
    
    private static boolean isOtherEntry(EditEntry entry, String column) {
        return entry != null && entry.column != null && entry.column.equals(column);
    }
    
    private void createCustomPicker(final EditEntry entry, final ArrayList<EditEntry> addTo) {
        final EditText label = new EditText(this);
        label.setKeyListener(TextKeyListener.getInstance(false, Capitalize.WORDS));
        label.requestFocus();
        new AlertDialog.Builder(this)
                .setView(label)
                .setTitle(R.string.customLabelPickerTitle)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        entry.setLabel(EditContactActivity.this, Email.TYPE_CUSTOM,
                                label.getText().toString());
                        mContactChanged = true;

                        if (addTo != null) {
                            addTo.add(entry);
                            buildViews();
                            entry.view.requestFocus(View.FOCUS_DOWN);
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
    
    /**
     * Saves or creates the contact based on the mode, and if sucessful finishes the activity.
     */
    private void doSaveAction() {
        // Save or create the contact if needed
        switch (mState) {
            case STATE_EDIT:
                save();
                break;

            case STATE_INSERT:
                create();
                break;

            default:
                Log.e(TAG, "Unknown state in doSaveOrCreate: " + mState);
                break;
        }
        finish();
    }
    
    /**
     * Save the various fields to the existing contact.
     */
    private void save() {
        ContentValues values = new ContentValues();
        String data;
        int numValues = 0;

        final long contactId = ContentUris.parseId(mUri);

        // Handle the name specially — DISPLAY_NAME em Contacts é agregado/somente
        // leitura; o nome de verdade mora numa linha StructuredName na tabela Data.
        final String name = mNameView.getText().toString();
        if (name != null && TextUtils.isGraphic(name)) {
            numValues++;
        }
        upsertStructuredName(contactId, name, mPhoneticNameView.getText().toString());

        long rawContactId = getPrimaryRawContactId(contactId);

        if (mPhotoChanged) {
            // Only write the photo if it's changed, since we don't initially load mPhoto.
            // Contacts.openContactPhotoOutputStream não existe mais - grava direto na
            // Data (mesmo padrão já usado em create(), abaixo).
            mResolver.delete(Data.CONTENT_URI,
                    Data.RAW_CONTACT_ID + "=? AND " + Data.MIMETYPE + "=?",
                    new String[] { String.valueOf(rawContactId),
                            android.provider.ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE });
            if (mPhoto != null) {
                java.io.ByteArrayOutputStream stream = new java.io.ByteArrayOutputStream();
                mPhoto.compress(Bitmap.CompressFormat.JPEG, 75, stream);
                ContentValues photoValues = new ContentValues();
                photoValues.put(Data.RAW_CONTACT_ID, rawContactId);
                photoValues.put(Data.MIMETYPE,
                        android.provider.ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE);
                photoValues.put(android.provider.ContactsContract.CommonDataKinds.Photo.PHOTO,
                        stream.toByteArray());
                mResolver.insert(Data.CONTENT_URI, photoValues);
            }
        }

        int entryCount = ContactEntryAdapter.countEntries(mSections, false);
        for (int i = 0; i < entryCount; i++) {
            EditEntry entry = ContactEntryAdapter.getEntry(mSections, i, false);
            int kind = entry.kind;
            data = entry.getData();
            boolean empty = data == null || !TextUtils.isGraphic(data);
            if (kind == EditEntry.KIND_CONTACT) {
                values.clear();
                if (Note.NOTE.equals(entry.column)) {
                    // Nota: linha própria em Data, não coluna do Contacts.
                    if (!empty) {
                        if (entry.id != 0) {
                            values.put(Note.NOTE, data);
                            mResolver.update(
                                    ContentUris.withAppendedId(Data.CONTENT_URI, entry.id),
                                    values, null, null);
                        } else {
                            values.put(Data.RAW_CONTACT_ID, rawContactId);
                            values.put(Data.MIMETYPE, Note.CONTENT_ITEM_TYPE);
                            values.put(Note.NOTE, data);
                            mResolver.insert(Data.CONTENT_URI, values);
                        }
                        numValues++;
                    } else if (entry.id != 0) {
                        mResolver.delete(ContentUris.withAppendedId(Data.CONTENT_URI, entry.id),
                                null, null);
                    }
                } else if (!empty) {
                    values.put(entry.column, data);
                    mResolver.update(entry.uri, values, null, null);
                    if (!Contacts.CUSTOM_RINGTONE.equals(entry.column) &&
                            !Contacts.SEND_TO_VOICEMAIL.equals(entry.column)) {
                        numValues++;
                    }
                } else {
                    values.put(entry.column, (String) null);
                    mResolver.update(entry.uri, values, null, null);
                }
            } else {
                if (!empty) {
                    values.clear();
                    entry.toValues(values);
                    if (entry.id != 0) {
                        mResolver.update(entry.uri, values, null, null);
                    } else {
                        values.put(Data.RAW_CONTACT_ID, rawContactId);
                        mResolver.insert(Data.CONTENT_URI, values);
                    }
                    if (!Contacts.CUSTOM_RINGTONE.equals(entry.column) &&
                            !Contacts.SEND_TO_VOICEMAIL.equals(entry.column)) {
                        numValues++;
                    }
                } else if (entry.id != 0) {
                    mResolver.delete(entry.uri, null, null);
                }
            }
        }

        if (numValues == 0) {
            // The contact is completely empty, delete it
            mResolver.delete(mUri, null, null);
            mUri = null;
            setResult(RESULT_CANCELED);
        } else {
            // People.addToMyContactsGroup removido: mesma simplificação já aplicada
            // em ContactsListActivity/create() — grupos de sync multi-conta não são
            // reconstruídos nesta migração, contato local não precisa de grupo.
            setResult(RESULT_OK, new Intent().setData(mUri));

            // Only notify user if we actually changed contact
            if (mContactChanged || mPhotoChanged) {
                Toast.makeText(this, R.string.contactSavedToast, Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Takes the entered data and saves it to a new contact.
     *
     * MIGRAÇÃO PROFUNDA: People.createPersonInMyContactsGroup() não existe mais.
     * O fluxo moderno é: 1) criar uma RawContacts (sem conta = contato só local,
     * sem sync — mesma decisão já tomada de não reconstruir a lógica de grupos de
     * sync multi-conta), 2) inserir uma linha StructuredName na tabela Data
     * referenciando essa RawContacts, 3) descobrir o Contacts._ID agregado
     * resultante pra usar dali em diante. Cada entry (telefone/email/...) também
     * precisa do RAW_CONTACT_ID nos values ao inserir — CONTACT_ID é agregado e
     * somente leitura, não serve para insert.
     */
    private void create() {
        ContentValues values = new ContentValues();
        String data;
        int numValues = 0;

        // Create the raw contact (local, sem conta de sync) + nome
        final String name = mNameView.getText().toString();
        final String phoneticName = mPhoneticNameView.getText().toString();
        if (name != null && TextUtils.isGraphic(name)) {
            numValues++;
        }

        ContentValues rawContactValues = new ContentValues();
        Uri rawContactUri = mResolver.insert(RawContacts.CONTENT_URI, rawContactValues);
        long rawContactId = ContentUris.parseId(rawContactUri);

        ContentValues nameValues = new ContentValues();
        nameValues.put(Data.RAW_CONTACT_ID, rawContactId);
        nameValues.put(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE);
        nameValues.put(StructuredName.DISPLAY_NAME, name);
        nameValues.put(StructuredName.PHONETIC_GIVEN_NAME, phoneticName);
        mResolver.insert(Data.CONTENT_URI, nameValues);

        // Descobre o Contacts._ID agregado que o sistema gerou pra essa raw contact
        long contactId = -1;
        Cursor rawCursor = mResolver.query(RawContacts.CONTENT_URI,
                new String[] { RawContacts.CONTACT_ID },
                RawContacts._ID + "=?", new String[] { String.valueOf(rawContactId) }, null);
        if (rawCursor != null) {
            try {
                if (rawCursor.moveToFirst() && !rawCursor.isNull(0)) {
                    contactId = rawCursor.getLong(0);
                }
            } finally {
                rawCursor.close();
            }
        }
        Uri contactUri = contactId >= 0
                ? ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId) : null;

        // Handle the photo — insere direto na Data (mais confiável que
        // openContactPhotoOutputStream logo após criar a raw contact, já que a
        // agregação em Contacts pode não estar pronta ainda no mesmo instante).
        if (mPhoto != null) {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            mPhoto.compress(Bitmap.CompressFormat.JPEG, 75, stream);
            ContentValues photoValues = new ContentValues();
            photoValues.put(Data.RAW_CONTACT_ID, rawContactId);
            photoValues.put(Data.MIMETYPE, android.provider.ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE);
            photoValues.put(android.provider.ContactsContract.CommonDataKinds.Photo.PHOTO,
                    stream.toByteArray());
            mResolver.insert(Data.CONTENT_URI, photoValues);
        }

        // Create the contact methods
        int entryCount = ContactEntryAdapter.countEntries(mSections, false);
        for (int i = 0; i < entryCount; i++) {
            EditEntry entry = ContactEntryAdapter.getEntry(mSections, i, false);
            if (entry.kind != EditEntry.KIND_CONTACT) {
                values.clear();
                if (entry.toValues(values)) {
                    // Only create the entry if there is data
                    values.put(Data.RAW_CONTACT_ID, rawContactId);
                    entry.uri = mResolver.insert(Data.CONTENT_URI, values);
                    entry.id = entry.uri != null ? ContentUris.parseId(entry.uri) : 0;
                    if (!Contacts.CUSTOM_RINGTONE.equals(entry.column) &&
                            !Contacts.SEND_TO_VOICEMAIL.equals(entry.column)) {
                        numValues++;
                    }
                }
            } else if (contactUri != null) {
                // Update the contact with any straggling data, like notes
                data = entry.getData();
                values.clear();
                if (data != null && TextUtils.isGraphic(data)) {
                    if (Note.NOTE.equals(entry.column)) {
                        // Nota é uma linha de Data própria, não coluna do Contacts
                        ContentValues noteValues = new ContentValues();
                        noteValues.put(Data.RAW_CONTACT_ID, rawContactId);
                        noteValues.put(Data.MIMETYPE, Note.CONTENT_ITEM_TYPE);
                        noteValues.put(Note.NOTE, data);
                        mResolver.insert(Data.CONTENT_URI, noteValues);
                    } else {
                        values.put(entry.column, data);
                        mResolver.update(contactUri, values, null, null);
                    }
                    if (!Contacts.CUSTOM_RINGTONE.equals(entry.column) &&
                            !Contacts.SEND_TO_VOICEMAIL.equals(entry.column)) {
                        numValues++;
                    }
                }
            }
        }

        if (numValues == 0) {
            mResolver.delete(ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId),
                    null, null);
            setResult(RESULT_CANCELED);
        } else {
            mUri = contactUri;
            Intent resultIntent = new Intent()
                    .setData(mUri)
                    .putExtra(Intent.EXTRA_SHORTCUT_NAME, name);
            setResult(RESULT_OK, resultIntent);
            Toast.makeText(this, R.string.contactCreatedToast, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Build up the entries to display on the screen.
     *
     * @param extras the extras used to start this activity, may be null
     */
    private void buildEntriesForEdit(Bundle extras) {
        Cursor personCursor = mResolver.query(mUri, CONTACT_PROJECTION, null, null, null);
        if (personCursor == null) {
            Log.e(TAG, "invalid contact uri: " + mUri);
            finish();
            return;
        } else if (!personCursor.moveToFirst()) {
            Log.e(TAG, "invalid contact uri: " + mUri);
            finish();
            personCursor.close();
            return;
        }

        final long contactId = ContentUris.parseId(mUri);

        // Clear out the old entries
        int numSections = mSections.size();
        for (int i = 0; i < numSections; i++) {
            mSections.get(i).clear();
        }

        EditEntry entry;

        // Name
        mNameView.setText(personCursor.getString(CONTACT_NAME_COLUMN));
        mNameView.addTextChangedListener(this);

        // Photo — mesmo padrão já usado em ViewContactActivity/AttachImage.
        mPhoto = null;
        try {
            java.io.InputStream photoStream = Contacts.openContactPhotoInputStream(
                    mResolver, mUri, true);
            if (photoStream != null) {
                mPhoto = android.graphics.BitmapFactory.decodeStream(photoStream);
                photoStream.close();
            }
        } catch (java.io.IOException e) {
            // sem foto, segue com mPhoto == null
        }
        if (mPhoto == null) {
            setPhotoPresent(false);
        } else {
            setPhotoPresent(true);
            mPhotoImageView.setImageBitmap(mPhoto);
        }

        // Organizations
        // Organization nao tem CONTENT_URI proprio (e um "data kind" dentro da
        // tabela Data) - consulta via Data.CONTENT_URI filtrando pelo MIMETYPE.
        Cursor organizationsCursor = mResolver.query(Data.CONTENT_URI,
                ORGANIZATIONS_PROJECTION,
                Organization.CONTACT_ID + "=? AND " + Data.MIMETYPE + "=?",
                new String[] { String.valueOf(contactId), Organization.CONTENT_ITEM_TYPE }, null);

        if (organizationsCursor != null) {
            while (organizationsCursor.moveToNext()) {
                int type = organizationsCursor.getInt(ORGANIZATIONS_TYPE_COLUMN);
                String label = organizationsCursor.getString(ORGANIZATIONS_LABEL_COLUMN);
                String company = organizationsCursor.getString(ORGANIZATIONS_COMPANY_COLUMN);
                String title = organizationsCursor.getString(ORGANIZATIONS_TITLE_COLUMN);
                long id = organizationsCursor.getLong(ORGANIZATIONS_ID_COLUMN);
                Uri uri = ContentUris.withAppendedId(Data.CONTENT_URI, id);

                // Add an organization entry
                entry = EditEntry.newOrganizationEntry(this, label, type, company, title, uri, id);
                entry.isPrimary = organizationsCursor.getLong(ORGANIZATIONS_ISPRIMARY_COLUMN) != 0;
                mOrgEntries.add(entry);
            }
            organizationsCursor.close();
        }

        // Notes — não é mais coluna direta do contato, é uma linha própria na tabela
        // Data (Note.CONTENT_ITEM_TYPE). getContactNoteRowId devolve o _id da linha
        // (ou -1 se ainda não existir), pra permitir update in-place ao salvar.
        String noteText = ContactEntryAdapter.getContactNote(this, contactId);
        long noteRowId = getContactNoteRowId(contactId);
        if (!TextUtils.isEmpty(noteText)) {
            Uri noteUri = noteRowId >= 0
                    ? ContentUris.withAppendedId(Data.CONTENT_URI, noteRowId) : null;
            entry = EditEntry.newNotesEntry(this, noteText, noteUri);
            entry.id = noteRowId >= 0 ? noteRowId : 0;
            mNoteEntries.add(entry);
        }

        // Ringtone / Send to voicemail — ainda são colunas diretas de Contacts.
        entry = EditEntry.newRingtoneEntry(this,
                personCursor.getString(CONTACT_CUSTOM_RINGTONE_COLUMN), mUri);
        mOtherEntries.add(entry);

        entry = EditEntry.newSendToVoicemailEntry(this,
                personCursor.getString(CONTACT_SEND_TO_VOICEMAIL_COLUMN), mUri);
        mOtherEntries.add(entry);

        // Phonetic name
        mPhoneticNameView.setText(personCursor.getString(CONTACT_PHONETIC_NAME_COLUMN));
        mPhoneticNameView.addTextChangedListener(this);

        personCursor.close();

        // Build up the phone entries
        Cursor phonesCursor = mResolver.query(Phone.CONTENT_URI, PHONES_PROJECTION,
                Phone.CONTACT_ID + "=?", new String[] { String.valueOf(contactId) }, null);

        if (phonesCursor != null) {
            while (phonesCursor.moveToNext()) {
                int type = phonesCursor.getInt(PHONES_TYPE_COLUMN);
                String label = phonesCursor.getString(PHONES_LABEL_COLUMN);
                String number = phonesCursor.getString(PHONES_NUMBER_COLUMN);
                long id = phonesCursor.getLong(PHONES_ID_COLUMN);
                boolean isPrimary = phonesCursor.getLong(PHONES_ISPRIMARY_COLUMN) != 0;
                Uri uri = ContentUris.withAppendedId(Phone.CONTENT_URI, id);

                // Add a phone number entry
                entry = EditEntry.newPhoneEntry(this, label, type, number, uri, id);
                entry.isPrimary = isPrimary;
                mPhoneEntries.add(entry);

                // Keep track of which primary types have been added
                if (type == Phone.TYPE_MOBILE) {
                    mMobilePhoneAdded = true;
                }
            }

            phonesCursor.close();
        }

        // Build the contact method entries (email/endereço/IM) via o cursor mesclado
        Cursor methodsCursor = ContactEntryAdapter.queryContactMethods(this, contactId, false);

        if (methodsCursor != null) {
            while (methodsCursor.moveToNext()) {
                int kind = methodsCursor.getInt(METHODS_KIND_COLUMN);
                String label = methodsCursor.getString(METHODS_LABEL_COLUMN);
                String data = methodsCursor.getString(METHODS_DATA_COLUMN);
                String auxData = methodsCursor.getString(METHODS_AUX_DATA_COLUMN);
                int type = methodsCursor.getInt(METHODS_TYPE_COLUMN);
                long id = methodsCursor.getLong(METHODS_ID_COLUMN);
                boolean isPrimary = methodsCursor.getLong(METHODS_ISPRIMARY_COLUMN) != 0;

                switch (kind) {
                    case KIND_EMAIL: {
                        Uri uri = ContentUris.withAppendedId(Email.CONTENT_URI, id);
                        entry = EditEntry.newEmailEntry(this, label, type, data, uri, id);
                        entry.isPrimary = isPrimary;
                        mEmailEntries.add(entry);
    
                        if (isPrimary) {
                            mPrimaryEmailAdded = true;
                        }
                        break;
                    }

                    case KIND_POSTAL: {
                        Uri uri = ContentUris.withAppendedId(StructuredPostal.CONTENT_URI, id);
                        entry = EditEntry.newPostalEntry(this, label, type, data, uri, id);
                        entry.isPrimary = isPrimary;
                        mPostalEntries.add(entry);
                        break;
                    }

                    case KIND_IM: {
                        Uri uri = ContentUris.withAppendedId(Data.CONTENT_URI, id);
                        // auxData guarda o protocolo (int) como string — sem decode
                        // especial, é só Integer.parseInt direto.
                        if (TextUtils.isEmpty(auxData)) {
                            Log.e(TAG, "IM sem protocolo, ignorando: " + id);
                            continue;
                        }
                        int protocol = Integer.parseInt(auxData);
                        entry = EditEntry.newImEntry(this,
                                getLabelsForKind(this, KIND_IM)[protocol], protocol,
                                data, uri, id);
                        mImEntries.add(entry);
                        break;
                    }
                }
            }

            methodsCursor.close();
        }

        // Add values from the extras, if there are any
        if (extras != null) {
            addFromExtras(extras);
        }

        // Add the base types if needed
        if (!mMobilePhoneAdded) {
            entry = EditEntry.newPhoneEntry(this, Phone.CONTENT_URI,
                    DEFAULT_PHONE_TYPE);
            mPhoneEntries.add(entry);
        }

        if (!mPrimaryEmailAdded) {
            entry = EditEntry.newEmailEntry(this, Email.CONTENT_URI,
                    DEFAULT_EMAIL_TYPE);
            entry.isPrimary = true;
            mEmailEntries.add(entry);
        }

        mContactChanged = false;
    }

    /**
     * Busca o _id da linha de Data (Note.CONTENT_ITEM_TYPE) do contato, ou -1 se
     * ainda não existir nenhuma nota salva.
     */
    private long getContactNoteRowId(long contactId) {
        Cursor c = mResolver.query(Data.CONTENT_URI, new String[] { Data._ID },
                Data.CONTACT_ID + "=? AND " + Data.MIMETYPE + "=?",
                new String[] { String.valueOf(contactId), Note.CONTENT_ITEM_TYPE }, null);
        try {
            if (c != null && c.moveToFirst()) {
                return c.getLong(0);
            }
        } finally {
            if (c != null) c.close();
        }
        return -1;
    }

    /**
     * Cria ou atualiza a linha StructuredName do contato — DISPLAY_NAME em
     * Contacts é agregado/somente leitura, então o nome de verdade precisa ser
     * escrito na tabela Data.
     */
    private void upsertStructuredName(long contactId, String name, String phoneticName) {
        Cursor c = mResolver.query(Data.CONTENT_URI, new String[] { Data._ID },
                Data.CONTACT_ID + "=? AND " + Data.MIMETYPE + "=?",
                new String[] { String.valueOf(contactId), StructuredName.CONTENT_ITEM_TYPE },
                null);
        long nameRowId = -1;
        try {
            if (c != null && c.moveToFirst()) {
                nameRowId = c.getLong(0);
            }
        } finally {
            if (c != null) c.close();
        }

        ContentValues values = new ContentValues();
        values.put(StructuredName.DISPLAY_NAME, name);
        values.put(StructuredName.PHONETIC_GIVEN_NAME, phoneticName);

        if (nameRowId >= 0) {
            mResolver.update(ContentUris.withAppendedId(Data.CONTENT_URI, nameRowId),
                    values, null, null);
        } else {
            long rawContactId = getPrimaryRawContactId(contactId);
            values.put(Data.RAW_CONTACT_ID, rawContactId);
            values.put(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE);
            mResolver.insert(Data.CONTENT_URI, values);
        }
    }

    /**
     * Devolve o _id da primeira RawContacts agregada nesse Contacts._ID. Contatos
     * podem ter várias raw contacts (uma por conta de sync); esta reescrita
     * simplifica pra sempre usar a primeira — suficiente pro caso comum de contato
     * local sem múltiplas contas sincronizadas.
     */
    private long getPrimaryRawContactId(long contactId) {
        Cursor c = mResolver.query(RawContacts.CONTENT_URI, new String[] { RawContacts._ID },
                RawContacts.CONTACT_ID + "=?", new String[] { String.valueOf(contactId) }, null);
        try {
            if (c != null && c.moveToFirst()) {
                return c.getLong(0);
            }
        } finally {
            if (c != null) c.close();
        }
        return -1;
    }

    /**
     * Build the list of EditEntries for full mode insertions.
     * 
     * @param extras the extras used to start this activity, may be null
     */
    private void buildEntriesForInsert(Bundle extras) {
        // Clear out the old entries
        int numSections = mSections.size();
        for (int i = 0; i < numSections; i++) {
            mSections.get(i).clear();
        }

        EditEntry entry;

        // Check the intent extras
        if (extras != null) {
            addFromExtras(extras);
        }

        // Photo
        mPhotoImageView.setImageResource(R.drawable.ic_contact_picture);

        // Add the base entries if they're not already present
        if (!mMobilePhoneAdded) {
            entry = EditEntry.newPhoneEntry(this, null, Phone.TYPE_MOBILE);
            entry.isPrimary = true;
            mPhoneEntries.add(entry);
        }

        if (!mPrimaryEmailAdded) {
            entry = EditEntry.newEmailEntry(this, null, DEFAULT_EMAIL_TYPE);
            entry.isPrimary = true;
            mEmailEntries.add(entry);
        }

        // Ringtone
        entry = EditEntry.newRingtoneEntry(this, null, mUri);
        mOtherEntries.add(entry);
        
        // Send to voicemail
        entry = EditEntry.newSendToVoicemailEntry(this, "0", mUri);
        mOtherEntries.add(entry);
    }

    private void addFromExtras(Bundle extras) {
        EditEntry entry;

        // Read the name from the bundle
        CharSequence name = extras.getCharSequence(Insert.NAME);
        if (name != null && TextUtils.isGraphic(name)) {
            mNameView.setText(name);
        }

        // Read the phonetic name from the bundle
        CharSequence phoneticName = extras.getCharSequence(Insert.PHONETIC_NAME);
        if (!TextUtils.isEmpty(phoneticName)) {
            mPhoneticNameView.setText(phoneticName);
        }

        // Postal entries from extras
        CharSequence postal = extras.getCharSequence(Insert.POSTAL);
        int postalType = extras.getInt(Insert.POSTAL_TYPE, INVALID_TYPE);
        if (!TextUtils.isEmpty(postal) && postalType == INVALID_TYPE) {
            postalType = DEFAULT_POSTAL_TYPE;
        }

        if (postalType != INVALID_TYPE) {
            entry = EditEntry.newPostalEntry(this, null, postalType, postal.toString(),
                    StructuredPostal.CONTENT_URI, 0);
            entry.isPrimary = extras.getBoolean(Insert.POSTAL_ISPRIMARY);
            mPostalEntries.add(entry);
        }

        // Email entries from extras
        addEmailFromExtras(extras, Insert.EMAIL, Insert.EMAIL_TYPE,
                Insert.EMAIL_ISPRIMARY);
        addEmailFromExtras(extras, Insert.SECONDARY_EMAIL, Insert.SECONDARY_EMAIL_TYPE,
                null);
        addEmailFromExtras(extras, Insert.TERTIARY_EMAIL, Insert.TERTIARY_EMAIL_TYPE,
                null);
   
        // Phone entries from extras
        addPhoneFromExtras(extras, Insert.PHONE, Insert.PHONE_TYPE,
                Insert.PHONE_ISPRIMARY);
        addPhoneFromExtras(extras, Insert.SECONDARY_PHONE, Insert.SECONDARY_PHONE_TYPE,
                null);
        addPhoneFromExtras(extras, Insert.TERTIARY_PHONE, Insert.TERTIARY_PHONE_TYPE,
                null);

        // IM entries from extras
        CharSequence imHandle = extras.getCharSequence(Insert.IM_HANDLE);
        CharSequence imProtocol = extras.getCharSequence(Insert.IM_PROTOCOL);
   
        if (imHandle != null && imProtocol != null) {
            // imProtocol chega como string numérica ("0", "1"...) — sem decode
            // especial, era só isso que o decodeImProtocol antigo fazia na prática
            // pra protocolos predefinidos.
            int protocol;
            try {
                protocol = Integer.parseInt(imProtocol.toString());
            } catch (NumberFormatException e) {
                protocol = -1;
            }
            if (protocol >= 0) {
                entry = EditEntry.newImEntry(this,
                        getLabelsForKind(this, KIND_IM)[protocol], protocol,
                        imHandle.toString(), Data.CONTENT_URI, 0);
            } else {
                entry = EditEntry.newImEntry(this, imProtocol.toString(), -1, imHandle.toString(),
                        Data.CONTENT_URI, 0);
            }
            entry.isPrimary = extras.getBoolean(Insert.IM_ISPRIMARY);
            mImEntries.add(entry);
        }
    }

    private void addEmailFromExtras(Bundle extras, String emailField,
            String typeField, String primaryField) {
        CharSequence email = extras.getCharSequence(emailField);
        
        // Correctly handle String in typeField as TYPE_CUSTOM 
        int emailType = INVALID_TYPE;
        String customLabel = null;
        if(extras.get(typeField) instanceof String) {
            emailType = Email.TYPE_CUSTOM;
            customLabel = extras.getString(typeField);
        } else {
            emailType = extras.getInt(typeField, INVALID_TYPE);
        }

        if (!TextUtils.isEmpty(email) && emailType == INVALID_TYPE) {
            emailType = DEFAULT_EMAIL_TYPE;
            mPrimaryEmailAdded = true;
        }

        if (emailType != INVALID_TYPE) {
            EditEntry entry = EditEntry.newEmailEntry(this, customLabel, emailType, email.toString(),
                    Email.CONTENT_URI, 0);
            entry.isPrimary = (primaryField == null) ? false : extras.getBoolean(primaryField);
            mEmailEntries.add(entry);

            // Keep track of which primary types have been added
            if (entry.isPrimary) {
                mPrimaryEmailAdded = true;
            }
        }
    }

    private void addPhoneFromExtras(Bundle extras, String phoneField,
            String typeField, String primaryField) {
        CharSequence phoneNumber = extras.getCharSequence(phoneField);
        
        // Correctly handle String in typeField as TYPE_CUSTOM 
        int phoneType = INVALID_TYPE;
        String customLabel = null;
        if(extras.get(typeField) instanceof String) {
            phoneType = Phone.TYPE_CUSTOM;
            customLabel = extras.getString(typeField);
        } else {
            phoneType = extras.getInt(typeField, INVALID_TYPE);
        }
        
        if (!TextUtils.isEmpty(phoneNumber) && phoneType == INVALID_TYPE) {
            phoneType = DEFAULT_PHONE_TYPE;
        }

        if (phoneType != INVALID_TYPE) {
            EditEntry entry = EditEntry.newPhoneEntry(this, customLabel, phoneType,
                    phoneNumber.toString(), Phone.CONTENT_URI, 0);
            entry.isPrimary = (primaryField == null) ? false : extras.getBoolean(primaryField);
            mPhoneEntries.add(entry);

            // Keep track of which primary types have been added
            if (phoneType == Phone.TYPE_MOBILE) {
                mMobilePhoneAdded = true;
            }
        }
    }

    /**
     * Removes all existing views, builds new ones for all the entries, and adds them.
     */
    private void buildViews() {
        // Remove existing views
        final LinearLayout layout = mLayout;
        layout.removeAllViews();
        
        buildViewsForSection(layout, mPhoneEntries,
                R.string.listSeparatorCallNumber_edit, SECTION_PHONES);
        buildViewsForSection(layout, mEmailEntries,
                R.string.listSeparatorSendEmail_edit, SECTION_EMAIL);
        buildViewsForSection(layout, mImEntries,
                R.string.listSeparatorSendIm_edit, SECTION_IM);
        buildViewsForSection(layout, mPostalEntries,
                R.string.listSeparatorMapAddress_edit, SECTION_POSTAL);
        buildViewsForSection(layout, mOrgEntries,
                R.string.listSeparatorOrganizations, SECTION_ORG);
        buildViewsForSection(layout, mNoteEntries,
                R.string.label_notes, SECTION_NOTE);
        
        buildOtherViews(layout, mOtherEntries);
    }


    /**
     * Builds the views for a specific section.
     * 
     * @param layout the container
     * @param section the section to build the views for
     */
    private void buildViewsForSection(final LinearLayout layout, ArrayList<EditEntry> section,
            int separatorResource, int sectionType) {
        
        View divider = mInflater.inflate(R.layout.edit_divider, layout, false);
        layout.addView(divider);
        
        // Count up undeleted children
        int activeChildren = 0;
        for (int i = section.size() - 1; i >= 0; i--) {
            EditEntry entry = section.get(i);
            if (!entry.isDeleted) {
                activeChildren++;
            }
        }
        
        // Build the correct group header based on undeleted children
        ViewGroup header;
        if (activeChildren == 0) {
            header = (ViewGroup) mInflater.inflate(R.layout.edit_separator_alone, layout, false);
        } else {
            header = (ViewGroup) mInflater.inflate(R.layout.edit_separator, layout, false);
        }

        // Because we're emulating a ListView, we need to handle focus changes
        // with some additional logic.
        header.setOnFocusChangeListener(this);
        
        TextView text = (TextView) header.findViewById(R.id.text);
        text.setText(getText(separatorResource));
        
        // Force TextView to always default color if we have children.  This makes sure
        // we don't change color when parent is pressed.
        if (activeChildren > 0) {
            ColorStateList stateList = text.getTextColors();
            text.setTextColor(stateList.getDefaultColor());
        }

        View addView = header.findViewById(R.id.separator);
        addView.setTag(Integer.valueOf(sectionType));
        addView.setOnClickListener(this);
        
        // Build views for the current section
        for (EditEntry entry : section) {
            entry.activity = this; // this could be null from when the state is restored
            if (!entry.isDeleted) {
                View view = buildViewForEntry(entry);
                header.addView(view);
            }
        }
        
        layout.addView(header);
    }
    
    private void buildOtherViews(final LinearLayout layout, ArrayList<EditEntry> section) {
        // Build views for the current section, putting a divider between each one
        for (EditEntry entry : section) {
            View divider = mInflater.inflate(R.layout.edit_divider, layout, false);
            layout.addView(divider);

            entry.activity = this; // this could be null from when the state is restored
            View view = buildViewForEntry(entry);
            view.setOnClickListener(this);
            layout.addView(view);
        }
        
        View divider = mInflater.inflate(R.layout.edit_divider, layout, false);
        layout.addView(divider);
    }
    
    /**
     * Builds a view to display an EditEntry.
     * 
     * @param entry the entry to display
     * @return a view that will display the given entry
     */
    /* package */ View buildViewForEntry(final EditEntry entry) {
        // Look for any existing entered text, and save it if found
        if (entry.view != null && entry.syncDataWithView) {
            String enteredText = ((TextView) entry.view.findViewById(R.id.data))
                    .getText().toString();
            if (!TextUtils.isEmpty(enteredText)) {
                entry.data = enteredText;
            }
        }

        // Build a new view
        final ViewGroup parent = mLayout;
        View view;

        // Because we're emulating a ListView, we might need to handle focus changes
        // with some additional logic.
        if (entry.kind == KIND_ORGANIZATION) {
            view = mInflater.inflate(R.layout.edit_contact_entry_org, parent, false);
        } else if (isOtherEntry(entry, Contacts.CUSTOM_RINGTONE)) {
            view = mInflater.inflate(R.layout.edit_contact_entry_ringtone, parent, false);
            view.setOnFocusChangeListener(this);
        } else if (isOtherEntry(entry, Contacts.SEND_TO_VOICEMAIL)) {
            view = mInflater.inflate(R.layout.edit_contact_entry_voicemail, parent, false);
            view.setOnFocusChangeListener(this);
        } else if (!entry.isStaticLabel) {
            view = mInflater.inflate(R.layout.edit_contact_entry, parent, false);
        } else {
            view = mInflater.inflate(R.layout.edit_contact_entry_static_label, parent, false);
        }
        entry.view = view;
        
        // Set the entry as the tag so we can find it again later given just the view
        view.setTag(entry);

        // Bind the label
        entry.bindLabel(this);

        // Bind data
        TextView data = (TextView) view.findViewById(R.id.data);
        TextView data2 = (TextView) view.findViewById(R.id.data2);
        
        if (data instanceof Button) {
            data.setOnClickListener(this);
        }
        if (data.length() == 0) {
            if (entry.syncDataWithView) {
                // If there is already data entered don't overwrite it
                data.setText(entry.data);
            } else {
                fillViewData(entry);
            }
        }
        if (data2 != null && data2.length() == 0) {
            // If there is already data entered don't overwrite it
            data2.setText(entry.data2);
        }
        data.setHint(entry.hint);
        if (data2 != null) data2.setHint(entry.hint2);
        if (entry.lines > 1) {
            data.setLines(entry.lines);
            data.setMaxLines(entry.maxLines);
            if (data2 != null) {
                data2.setLines(entry.lines);
                data2.setMaxLines(entry.maxLines);
            }
        }
        int contentType = entry.contentType;
        if (contentType != EditorInfo.TYPE_NULL) {
            data.setInputType(contentType);
            if (data2 != null) {
                data2.setInputType(contentType);
            }
            if ((contentType&EditorInfo.TYPE_MASK_CLASS)
                    == EditorInfo.TYPE_CLASS_PHONE) {
                data.addTextChangedListener(new PhoneNumberFormattingTextWatcher());
                if (data2 != null) {
                    data2.addTextChangedListener(new PhoneNumberFormattingTextWatcher());
                }
            }
        }
        
        // Give focus to children as requested, possibly after a configuration change
        View focusChild = view.findViewById(entry.requestFocusId);
        if (focusChild != null) {
            focusChild.requestFocus();
            if (focusChild instanceof EditText) {
                ((EditText) focusChild).setSelection(entry.requestCursor);
            }
        }
        
        // Reset requested focus values
        entry.requestFocusId = View.NO_ID;
        entry.requestCursor = 0;

        // Connect listeners up to watch for changed values.
        if (data instanceof EditText) {
            data.addTextChangedListener(this);
        }
        if (data2 instanceof EditText) {
            data2.addTextChangedListener(this);
        }

        // Hook up the delete button
        View delete = view.findViewById(R.id.delete);
        if (delete != null) delete.setOnClickListener(this);
        
        return view;
    }

    private void fillViewData(final EditEntry entry) {
        if (isOtherEntry(entry, Contacts.CUSTOM_RINGTONE)) {
            updateRingtoneView(entry);
        } else if (isOtherEntry(entry, Contacts.SEND_TO_VOICEMAIL)) {
            CheckBox checkBox = (CheckBox) entry.view.findViewById(R.id.checkbox);
            boolean sendToVoicemail = false;
            if (entry.data != null) {
                sendToVoicemail = (Integer.valueOf(entry.data) == 1);
            }
            checkBox.setChecked(sendToVoicemail);
        }
    }
    
    /**
     * Handles the results from the label change picker.
     */
    private final class LabelPickedListener implements DialogInterface.OnClickListener {
        EditEntry mEntry;
        String[] mLabels;

        public LabelPickedListener(EditEntry entry, String[] labels) {
            mEntry = entry;
            mLabels = labels;
        }

        public void onClick(DialogInterface dialog, int which) {
            // TODO: Use a managed dialog
            if (mEntry.kind != KIND_IM) {
                final int type = getTypeFromLabelPosition(mLabels, which);
                if (type == Email.TYPE_CUSTOM) {
                    createCustomPicker(mEntry, null);
                } else {
                    mEntry.setLabel(EditContactActivity.this, type, mLabels[which]);
                    mContactChanged = true;
                }
            } else {
                mEntry.setLabel(EditContactActivity.this, which, mLabels[which]);
                mContactChanged = true;
            }
        }
    }

    /**
     * A basic structure with the data for a contact entry in the list.
     */
    private static final class EditEntry extends ContactEntryAdapter.Entry implements Parcelable {
        // These aren't stuffed into the parcel
        public EditContactActivity activity;
        public View view;

        // These are stuffed into the parcel
        public String hint;
        public String hint2;
        public String column;
        public String contentDirectory;
        public String data2;
        public int contentType;
        public int type;
        /**
         * If 0 or 1, setSingleLine will be called. If negative, setSingleLine
         * will not be called.
         */
        public int lines = 1;
        public boolean isPrimary;
        public boolean isDeleted = false;
        public boolean isStaticLabel = false;
        public boolean syncDataWithView = true;

        /**
         * Request focus on the child of this {@link EditEntry} found using
         * {@link View#findViewById(int)}. This value should be reset to
         * {@link View#NO_ID} after each use.
         */
        public int requestFocusId = View.NO_ID;

        /**
         * If the {@link #requestFocusId} is an {@link EditText}, this value
         * indicates the requested cursor position placement.
         */
        public int requestCursor = 0;

        private EditEntry() {
            // only used by CREATOR
        }

        public EditEntry(EditContactActivity activity) {
            this.activity = activity;
        }

        public EditEntry(EditContactActivity activity, String label,
                int type, String data, Uri uri, long id) {
            this.activity = activity;
            this.isPrimary = false;
            this.label = label;
            this.type = type;
            this.data = data;
            this.uri = uri;
            this.id = id;
        }

        public int describeContents() {
            return 0;
        }

        public void writeToParcel(Parcel parcel, int flags) {
            // Make sure to read data from the input field, if anything is entered
            data = getData();

            // Write in our own fields.
            parcel.writeString(hint);
            parcel.writeString(hint2);
            parcel.writeString(column);
            parcel.writeString(contentDirectory);
            parcel.writeString(data2);
            parcel.writeInt(contentType);
            parcel.writeInt(type);
            parcel.writeInt(lines);
            parcel.writeInt(isPrimary ? 1 : 0);
            parcel.writeInt(isDeleted ? 1 : 0);
            parcel.writeInt(isStaticLabel ? 1 : 0);
            parcel.writeInt(syncDataWithView ? 1 : 0);

            // Write in the fields from Entry
            super.writeToParcel(parcel);
        }

        public static final Parcelable.Creator<EditEntry> CREATOR =
            new Parcelable.Creator<EditEntry>() {
            public EditEntry createFromParcel(Parcel in) {
                EditEntry entry = new EditEntry();

                // Read out our own fields
                entry.hint = in.readString();
                entry.hint2 = in.readString();
                entry.column = in.readString();
                entry.contentDirectory = in.readString();
                entry.data2 = in.readString();
                entry.contentType = in.readInt();
                entry.type = in.readInt();
                entry.lines = in.readInt();
                entry.isPrimary = in.readInt() == 1;
                entry.isDeleted = in.readInt() == 1;
                entry.isStaticLabel = in.readInt() == 1;
                entry.syncDataWithView = in.readInt() == 1;
                
                // Read out the fields from Entry
                entry.readFromParcel(in);

                return entry;
            }
            
            public EditEntry[] newArray(int size) {
                return new EditEntry[size];
            }
        };

        public void setLabel(Context context, int typeIn, String labelIn) {
            type = typeIn;
            label = labelIn;
            if (view != null) {
                bindLabel(context);
            }
        }
        
        public void bindLabel(Context context) {
            TextView v = (TextView) view.findViewById(R.id.label);
            if (isStaticLabel) {
                v.setText(label);
                return;
            }

            switch (kind) {
                case KIND_PHONE: {
                    v.setText(Phone.getTypeLabel(activity.getResources(), type, label));
                    break;
                }

                case KIND_IM: {
                    v.setText(getLabelsForKind(activity, kind)[type]);
                    break;
                }
                
                case KIND_ORGANIZATION: {
                    v.setText(Organization.getTypeLabel(activity.getResources(), type, label));
                    break;
                }

                case KIND_EMAIL: {
                    v.setText(Email.getTypeLabel(activity.getResources(), type, label));
                    break;
                }

                case KIND_POSTAL: {
                    v.setText(StructuredPostal.getTypeLabel(activity.getResources(), type, label));
                    v.setMaxLines(3);
                    break;
                }

                default: {
                    v.setText(label);
                    break;
                }
            }
            v.setOnClickListener(activity);
        }

        /**
         * Returns the data for the entry
         * @return the data for the entry
         */
        public String getData() {
            if (view != null && syncDataWithView) {
                CharSequence text = ((TextView) view.findViewById(R.id.data)).getText();
                if (text != null) {
                    return text.toString();
                }
            }

            if (data != null) {
                return data.toString();
            }

            return null;
        }

        /**
         * Dumps the entry into a HashMap suitable for passing to the database.
         * 
         * @param values the HashMap to fill in.
         * @return true if the value should be saved, false otherwise
         */
        public boolean toValues(ContentValues values) {
            boolean success = false;
            String labelString = null;
            // Save the type and label
            if (view != null) {
                // Read the possibly updated label from the text field
                labelString = ((TextView) view.findViewById(R.id.label)).getText().toString();
            }
            switch (kind) {
                case KIND_PHONE:
                    if (type != Phone.TYPE_CUSTOM) {
                        labelString = null;
                    }
                    values.put(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
                    values.put(Phone.LABEL, labelString);
                    values.put(Phone.TYPE, type);
                    break;

                case KIND_EMAIL:
                    if (type != Email.TYPE_CUSTOM) {
                        labelString = null;
                    }
                    values.put(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE);
                    values.put(Email.LABEL, labelString);
                    values.put(Email.TYPE, type);
                    break;

                case KIND_IM:
                    // auxData (aux_data no modelo antigo) agora é só o protocolo (int)
                    // como string — sem encode especial. type == -1 significa protocolo
                    // customizado, aí o "label" já é o nome do protocolo customizado.
                    values.put(Data.MIMETYPE, Im.CONTENT_ITEM_TYPE);
                    values.putNull(Im.LABEL);
                    if (type != -1) {
                        values.put(Im.PROTOCOL, type);
                    } else {
                        values.put(Im.PROTOCOL, Im.PROTOCOL_CUSTOM);
                        values.put(Im.CUSTOM_PROTOCOL, label != null ? label.toString() : null);
                    }
                    break;

                case KIND_POSTAL:
                    if (type != StructuredPostal.TYPE_CUSTOM) {
                        labelString = null;
                    }
                    values.put(Data.MIMETYPE, StructuredPostal.CONTENT_ITEM_TYPE);
                    values.put(StructuredPostal.LABEL, labelString);
                    values.put(StructuredPostal.TYPE, type);
                    break;

                case KIND_ORGANIZATION:
                    if (type != Organization.TYPE_CUSTOM) {
                        labelString = null;
                    }
                    values.put(Data.MIMETYPE, Organization.CONTENT_ITEM_TYPE);
                    values.put(Organization.LABEL, labelString);
                    values.put(Organization.TYPE, type);
                    // Save the title
                    if (view != null) {
                        // Read the possibly updated data from the text field
                        data2 = ((TextView) view.findViewById(R.id.data2)).getText().toString();
                    }
                    if (!TextUtils.isGraphic(data2)) {
                        values.putNull(Organization.TITLE);
                    } else {
                        values.put(Organization.TITLE, data2.toString());
                        success = true;
                    }
                    break;

                default:
                    Log.w(TAG, "unknown kind " + kind);
                    break;
            }

            // Only set the ISPRIMARY flag if part of the incoming data.  This is because the
            // ContentProvider will try finding a new primary when setting to false, meaning
            // it's possible to lose primary altogether as we walk down the list.  If this editor
            // implements editing of primaries in the future, this will need to be revisited.
            if (isPrimary) {
                values.put(Data.IS_PRIMARY, 1);
            }

            // Save the data
            if (view != null && syncDataWithView) {
                // Read the possibly updated data from the text field
                data = ((TextView) view.findViewById(R.id.data)).getText().toString();
            }
            if (!TextUtils.isGraphic(data)) {
                values.putNull(column);
                return success;
            } else {
                values.put(column, data.toString());
                return true;
            }
        }

        /**
         * Create a new empty organization entry
         */
        public static final EditEntry newOrganizationEntry(EditContactActivity activity,
                Uri uri, int type) {
            return newOrganizationEntry(activity, null, type, null, null, uri, 0);
        }

        /**
         * Create a new company entry with the given data.
         */
        public static final EditEntry newOrganizationEntry(EditContactActivity activity,
                String label, int type, String company, String title, Uri uri, long id) {
            EditEntry entry = new EditEntry(activity, label, type, company, uri, id);
            entry.hint = activity.getString(R.string.ghostData_company);
            entry.hint2 = activity.getString(R.string.ghostData_title);
            entry.data2 = title;
            entry.column = Organization.COMPANY;
            entry.contentDirectory = Data.CONTENT_URI.toString();
            entry.kind = KIND_ORGANIZATION;
            entry.contentType = EditorInfo.TYPE_CLASS_TEXT
                    | EditorInfo.TYPE_TEXT_FLAG_CAP_WORDS;
            return entry;
        }

        /**
         * Create a new notes entry with the given data.
         */
        public static final EditEntry newNotesEntry(EditContactActivity activity,
                String data, Uri uri) {
            EditEntry entry = new EditEntry(activity);
            entry.label = activity.getString(R.string.label_notes);
            entry.hint = activity.getString(R.string.ghostData_notes);
            entry.data = data;
            entry.uri = uri;
            entry.column = Note.NOTE;
            entry.maxLines = 10;
            entry.lines = 2;
            entry.id = 0;
            entry.kind = KIND_CONTACT;
            entry.contentType = EditorInfo.TYPE_CLASS_TEXT
                    | EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES
                    | EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE;
            entry.isStaticLabel = true;
            return entry;
        }

        /**
         * Create a new ringtone entry with the given data.
         */
        public static final EditEntry newRingtoneEntry(EditContactActivity activity,
                String data, Uri uri) {
            EditEntry entry = new EditEntry(activity);
            entry.label = activity.getString(R.string.label_ringtone);
            entry.data = data;
            entry.uri = uri;
            entry.column = Contacts.CUSTOM_RINGTONE;
            entry.kind = KIND_CONTACT;
            entry.isStaticLabel = true;
            entry.syncDataWithView = false;
            entry.lines = -1;
            return entry;
        }

        /**
         * Create a new send-to-voicemail entry with the given data.
         */
        public static final EditEntry newSendToVoicemailEntry(EditContactActivity activity,
                String data, Uri uri) {
            EditEntry entry = new EditEntry(activity);
            entry.label = activity.getString(R.string.actionIncomingCall);
            entry.data = data;
            entry.uri = uri;
            entry.column = Contacts.SEND_TO_VOICEMAIL;
            entry.kind = KIND_CONTACT;
            entry.isStaticLabel = true;
            entry.syncDataWithView = false;
            entry.lines = -1;
            return entry;
        }

        /**
         * Create a new empty email entry
         */
        public static final EditEntry newPhoneEntry(EditContactActivity activity,
                Uri uri, int type) {
            return newPhoneEntry(activity, null, type, null, uri, 0);
        }

        /**
         * Create a new phone entry with the given data.
         */
        public static final EditEntry newPhoneEntry(EditContactActivity activity,
                String label, int type, String data, Uri uri,
                long id) {
            EditEntry entry = new EditEntry(activity, label, type, data, uri, id);
            entry.hint = activity.getString(R.string.ghostData_phone);
            entry.column = Phone.NUMBER;
            entry.contentDirectory = Phone.CONTENT_URI.toString();
            entry.kind = KIND_PHONE;
            entry.contentType = EditorInfo.TYPE_CLASS_PHONE;
            return entry;
        }

        /**
         * Create a new empty email entry
         */
        public static final EditEntry newEmailEntry(EditContactActivity activity,
                Uri uri, int type) {
            return newEmailEntry(activity, null, type, null, uri, 0);
        }

        /**
         * Create a new email entry with the given data.
         */
        public static final EditEntry newEmailEntry(EditContactActivity activity,
                String label, int type, String data, Uri uri,
                long id) {
            EditEntry entry = new EditEntry(activity, label, type, data, uri, id);
            entry.hint = activity.getString(R.string.ghostData_email);
            entry.column = Data.DATA1;
            entry.contentDirectory = Email.CONTENT_URI.toString();
            entry.kind = KIND_EMAIL;
            entry.contentType = EditorInfo.TYPE_CLASS_TEXT
                    | EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS;
            return entry;
        }

        /**
         * Create a new empty postal address entry
         */
        public static final EditEntry newPostalEntry(EditContactActivity activity,
                Uri uri, int type) {
            return newPostalEntry(activity, null, type, null, uri, 0);
        }

        /**
         * Create a new postal address entry with the given data.
         *
         * @param label label for the item, from the db not the display label
         * @param type the type of postal address
         * @param data the starting data for the entry, may be null
         * @param uri the uri for the entry if it already exists, may be null
         * @param id the id for the entry if it already exists, 0 it it doesn't
         * @return the new EditEntry
         */
        public static final EditEntry newPostalEntry(EditContactActivity activity,
                String label, int type, String data, Uri uri, long id) {
            EditEntry entry = new EditEntry(activity, label, type, data, uri, id);
            entry.hint = activity.getString(R.string.ghostData_postal);
            entry.column = Data.DATA1;
            entry.contentDirectory = StructuredPostal.CONTENT_URI.toString();
            entry.kind = KIND_POSTAL;
            entry.contentType = EditorInfo.TYPE_CLASS_TEXT
                    | EditorInfo.TYPE_TEXT_VARIATION_POSTAL_ADDRESS
                    | EditorInfo.TYPE_TEXT_FLAG_CAP_WORDS
                    | EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE;
            entry.maxLines = 4;
            entry.lines = 2;
            return entry;
        }

        /**
         * Create a new IM address entry
         */
        public static final EditEntry newImEntry(EditContactActivity activity,
                Uri uri, int type) {
            return newImEntry(activity, null, type, null, uri, 0);
        }

        /**
         * Create a new IM address entry with the given data.
         *
         * @param label label for the item, from the db not the display label
         * @param protocol the type used
         * @param data the starting data for the entry, may be null
         * @param uri the uri for the entry if it already exists, may be null
         * @param id the id for the entry if it already exists, 0 it it doesn't
         * @return the new EditEntry
         */
        public static final EditEntry newImEntry(EditContactActivity activity,
                String label, int protocol, String data, Uri uri, long id) {
            EditEntry entry = new EditEntry(activity, label, protocol, data, uri, id);
            entry.hint = activity.getString(R.string.ghostData_im);
            entry.column = Data.DATA1;
            entry.contentDirectory = Data.CONTENT_URI.toString();
            entry.kind = KIND_IM;
            entry.contentType = EditorInfo.TYPE_CLASS_TEXT
                    | EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS;
            return entry;
        }
    }

    public void afterTextChanged(Editable s) {
        // Someone edited a text field, so assume this contact is changed
        mContactChanged = true;
    }

    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        // Do nothing; editing handled by afterTextChanged()
    }

    public void onTextChanged(CharSequence s, int start, int before, int count) {
        // Do nothing; editing handled by afterTextChanged()
    }
    
    public void onFocusChange(View v, boolean hasFocus) {
        // Because we're emulating a ListView, we need to setSelected() for
        // views as they are focused.
        v.setSelected(hasFocus);
    }
}
