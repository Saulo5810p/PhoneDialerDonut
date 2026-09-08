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

package com.android.phone;

import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Intents.Insert;
import android.provider.ContactsContract.RawContacts;
import android.text.TextUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

/**
 * SIM Address Book UI for the Phone app.
 */
public class SimContacts extends ADNList {
    private static final int MENU_IMPORT_ONE = 1;
    private static final int MENU_IMPORT_ALL = 2;
    private ProgressDialog mProgressDialog;

    
    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        registerForContextMenu(getListView());
    }

    private class ImportAllThread extends Thread implements OnCancelListener, OnClickListener {
        boolean mCanceled = false;
        
        public ImportAllThread() {
            super("ImportAllThread");
        }
        
        @Override
        public void run() {
            ContentValues map = new ContentValues();
            ContentResolver cr = getContentResolver();
            Object[] parsed = new Object[2];
            
            mCursor.moveToPosition(-1);
            while (!mCanceled && mCursor.moveToNext()) {
                String name = mCursor.getString(0);
                String number = mCursor.getString(1);

                Uri personUrl = parseName(name, parsed);

                if (personUrl == null) {
                    // People.createPersonInMyContactsGroup não existe mais — cria um
                    // contato local (sem conta de sync) via RawContacts+StructuredName,
                    // mesmo padrão já usado em EditContactActivity.create().
                    ContentValues rawValues = new ContentValues();
                    Uri rawContactUri = cr.insert(RawContacts.CONTENT_URI, rawValues);
                    if (rawContactUri == null) {
                        Log.e(TAG, "Error inserting raw contact for " + name);
                        continue;
                    }
                    long rawContactId = ContentUris.parseId(rawContactUri);

                    ContentValues nameValues = new ContentValues();
                    nameValues.put(Data.RAW_CONTACT_ID, rawContactId);
                    nameValues.put(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE);
                    nameValues.put(StructuredName.DISPLAY_NAME, (String) parsed[0]);
                    cr.insert(Data.CONTENT_URI, nameValues);

                    long contactId = -1;
                    Cursor rc = cr.query(RawContacts.CONTENT_URI,
                            new String[] { RawContacts.CONTACT_ID },
                            RawContacts._ID + "=?",
                            new String[] { String.valueOf(rawContactId) }, null);
                    if (rc != null) {
                        try {
                            if (rc.moveToFirst() && !rc.isNull(0)) {
                                contactId = rc.getLong(0);
                            }
                        } finally {
                            rc.close();
                        }
                    }
                    if (contactId < 0) {
                        Log.e(TAG, "Error resolvendo contact id pra " + name);
                        continue;
                    }
                    personUrl = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId);

                    map.clear();
                    map.put(Data.RAW_CONTACT_ID, rawContactId);
                    map.put(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
                    map.put(Phone.NUMBER, number);
                    map.put(Phone.TYPE, (Integer) parsed[1]);
                    Uri numberUrl = cr.insert(Data.CONTENT_URI, map);

                    mProgressDialog.incrementProgressBy(1);
                    if (numberUrl == null) {
                        Log.e(TAG, "Error inserting phone " + map + " for person " +
                                personUrl + ", removing person");
                        continue;
                    }
                    continue;
                }

                // Contato já existe — adiciona o telefone via raw contact dele
                long existingContactId = ContentUris.parseId(personUrl);
                long existingRawContactId = getPrimaryRawContactId(cr, existingContactId);
                map.clear();
                map.put(Data.RAW_CONTACT_ID, existingRawContactId);
                map.put(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
                map.put(Phone.NUMBER, number);
                map.put(Phone.TYPE, (Integer) parsed[1]);
                Uri numberUrl = cr.insert(Data.CONTENT_URI, map);
                
                mProgressDialog.incrementProgressBy(1);
                if (numberUrl == null) {
                    Log.e(TAG, "Error inserting phone " + map + " for person " +
                            personUrl + ", removing person");
                    continue;
                }
            }
            
            mProgressDialog.dismiss();

            finish();
        }

        public void onCancel(DialogInterface dialog) {
            mCanceled = true;
        }

        public void onClick(DialogInterface dialog, int which) {
            mCanceled = true;
            mProgressDialog.dismiss();
        }
    }

    private static long getPrimaryRawContactId(ContentResolver cr, long contactId) {
        Cursor c = cr.query(RawContacts.CONTENT_URI, new String[] { RawContacts._ID },
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

    @Override
    protected CursorAdapter newAdapter() {
        return new SimpleCursorAdapter(this, R.layout.sim_import_list_entry, mCursor,
                new String[] { "name" }, new int[] { android.R.id.text1 });
    }

    @Override
    protected Uri resolveIntent() {
        Intent intent = getIntent();
        intent.setData(Uri.parse("content://icc/adn"));
        if (Intent.ACTION_PICK.equals(intent.getAction())) {
            // "index" is 1-based
            mInitialSelection = intent.getIntExtra("index", 0) - 1;
        }
        return intent.getData();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, MENU_IMPORT_ALL, 0, R.string.importAllSimEntries);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_IMPORT_ALL:
                CharSequence title = getString(R.string.importAllSimEntries);
                CharSequence message = getString(R.string.importingSimContacts); 

                ImportAllThread thread = new ImportAllThread();

                mProgressDialog = new ProgressDialog(this);
                mProgressDialog.setTitle(title);
                mProgressDialog.setMessage(message);
                mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                mProgressDialog.setButton(getString(R.string.cancel), thread);
                mProgressDialog.setProgress(0);
                mProgressDialog.setMax(mCursor.getCount());
                mProgressDialog.show();
                
                thread.start();
                
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_IMPORT_ONE:
                ContextMenu.ContextMenuInfo menuInfo = item.getMenuInfo();
                if (menuInfo instanceof AdapterView.AdapterContextMenuInfo) {
                    int position = ((AdapterView.AdapterContextMenuInfo)menuInfo).position;
                    importOne(position);
                    return true;
                }
        }
        return super.onContextItemSelected(item);
    }

    
    public void onCreateContextMenu(ContextMenu menu, View v,
            ContextMenu.ContextMenuInfo menuInfo) {
        if (menuInfo instanceof AdapterView.AdapterContextMenuInfo) {
            AdapterView.AdapterContextMenuInfo itemInfo =
                    (AdapterView.AdapterContextMenuInfo) menuInfo;
            TextView textView = (TextView) itemInfo.targetView.findViewById(android.R.id.text1);
            if (textView != null) {
                menu.setHeaderTitle(textView.getText());
            }
            menu.add(0, MENU_IMPORT_ONE, 0, R.string.importSimEntry);
        }
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        importOne(position);
    }

    private void importOne(int position) {
        if (mCursor.moveToPosition(position)) {
            String name = mCursor.getString(NAME_COLUMN);
            String number = mCursor.getString(NUMBER_COLUMN);
            Object[] parsed = new Object[2];
            Uri personUrl = parseName(name, parsed);

            Intent intent;
            if (personUrl == null) {
                // Add a new contact
                intent = new Intent(Insert.ACTION, Contacts.CONTENT_URI);
                intent.putExtra(Insert.NAME, (String)parsed[0]);
                intent.putExtra(Insert.PHONE, number);
                intent.putExtra(Insert.PHONE_TYPE, ((Integer)parsed[1]).intValue());
            } else {
                // Add the number to an existing contact
                intent = new Intent(Intent.ACTION_EDIT, personUrl);
                intent.putExtra(Insert.PHONE, number);
                intent.putExtra(Insert.PHONE_TYPE, ((Integer)parsed[1]).intValue());
            }
            startActivity(intent);
        }
    }

    /**
     * Parse the name looking for /W /H /M or /O at the end, signifying the type.
     *
     * @param name The name from the SIM card
     * @param parsed slot 0 is filled in with the name, and slot 1 is filled
     * in with the type
     */
    private Uri parseName(String name, Object[] parsed) {
        // default to TYPE_MOBILE so you can send SMSs to the numbers
        int type = Phone.TYPE_MOBILE;

        // Look for /W /H /M or /O at the end of the name signifying the type
        int nameLen = name.length();
        if (nameLen - 2 >= 0 && name.charAt(nameLen - 2) == '/') {
            char c = Character.toUpperCase(name.charAt(nameLen - 1));
            if (c == 'W') {
                type = Phone.TYPE_WORK;
            } else if (c == 'M') {
                type = Phone.TYPE_MOBILE;
            } else if (c == 'H') {
                type = Phone.TYPE_HOME;
            } else if (c == 'O') {
                type = Phone.TYPE_MOBILE;
            }
            name = name.substring(0, nameLen - 2);
        }
        parsed[0] = name;
        parsed[1] = type;

        StringBuilder where = new StringBuilder(Contacts.DISPLAY_NAME);
        where.append('=');
        DatabaseUtils.appendEscapedSQLString(where, name);
        Uri url = null;
        Cursor c = getContentResolver().query(Contacts.CONTENT_URI,
                new String[] {Contacts._ID},
                where.toString(), null, null);
        if (c != null) {
            if (c.moveToFirst()) {
                url = ContentUris.withAppendedId(Contacts.CONTENT_URI, c.getLong(0));
            }
            c.close();
        }
        return url;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_CALL: {
                if (mCursor != null && mCursor.moveToPosition(getSelectedItemPosition())) {
                    String number = mCursor.getString(NUMBER_COLUMN);
                    if (number == null || !TextUtils.isGraphic(number)) {
                        // There is no number entered.
                        //TODO play error sound or something...
                        return true;
                    }
                    Intent intent = new Intent(Intent.ACTION_CALL_PRIVILEGED,
                            Uri.fromParts("tel", number, null));
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                          | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                    startActivity(intent);
                    finish();
                    return true;
                }
            }
        }
        return super.onKeyDown(keyCode, event);
    }
}
