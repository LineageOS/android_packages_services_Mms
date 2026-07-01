/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.mms.service;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertThrows;

import android.Manifest;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.telephony.TelephonyManager;

import androidx.test.core.app.ApplicationProvider;

import com.android.internal.telephony.IMms;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowBinder;
import org.robolectric.shadows.ShadowPackageManager;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Field;

@RunWith(RobolectricTestRunner.class)
public final class MmsServiceRoboTest {
    private IMms.Stub binder;
    private MmsService mmsService;
    private static final int CALLING_USER = 10;

    public static class TestMmsService extends MmsService {
        private TelephonyManager mMockTelephonyManager;

        @Override
        public void onCreate() {
            mMockTelephonyManager = mock(TelephonyManager.class);
            when(mMockTelephonyManager.createForSubscriptionId(anyInt()))
                    .thenReturn(mMockTelephonyManager);
            when(mMockTelephonyManager.getDataNetworkType())
                    .thenReturn(TelephonyManager.NETWORK_TYPE_LTE);
            super.onCreate();
        }

        @Override
        public Object getSystemService(String name) {
            if ("stats".equals(name)) {
                return null;
            }
            if (Context.TELEPHONY_SERVICE.equals(name) || "phone".equals(name)) {
                return mMockTelephonyManager;
            }
            return super.getSystemService(name);
        }
    }

    public static class TestVoicemailProvider extends ContentProvider {
        public static String sSourcePackage = null;
        public static ParcelFileDescriptor sPfd = null;

        @Override
        public boolean onCreate() { return true; }

        @Override
        public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
            MatrixCursor cursor = new MatrixCursor(new String[] { "source_package" });
            cursor.addRow(new Object[] { sSourcePackage });
            return cursor;
        }

        @Override
        public ParcelFileDescriptor openFile(Uri uri, String mode) {
            return sPfd;
        }

        @Override
        public String getType(Uri uri) { return null; }
        @Override
        public Uri insert(Uri uri, ContentValues values) { return null; }
        @Override
        public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
        @Override
        public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
    }

    @Before
    public void setUp() {
        ClassLoader cl = MmsService.class.getClassLoader();
        try {
            Class<?> flagsClass = Class.forName("com.android.internal.telephony.flags.Flags", true, cl);
            Field field = flagsClass.getDeclaredField("FEATURE_FLAGS");
            field.setAccessible(true);
            Class<?> featureFlagsClass = Class.forName("com.android.internal.telephony.flags.FeatureFlags", true, cl);
            Object mockFeatureFlags = mock(featureFlagsClass);
            field.set(null, mockFeatureFlags);
        } catch (Throwable e) {
            // Ignore if flags are not generated/present in this build
        }

        try {
            Class<?> phoneFactoryClass = Class.forName("com.android.internal.telephony.PhoneFactory", true, cl);

            Field sPhonesField = phoneFactoryClass.getDeclaredField("sPhones");
            sPhonesField.setAccessible(true);

            Class<?> phoneClass = Class.forName("com.android.internal.telephony.Phone", true, cl);
            Object mockPhone = mock(phoneClass);

            Object mockPhones = Array.newInstance(phoneClass, 2);
            Array.set(mockPhones, 0, mockPhone);
            Array.set(mockPhones, 1, mockPhone);
            sPhonesField.set(null, mockPhones);

            Field sPhoneField = phoneFactoryClass.getDeclaredField("sPhone");
            sPhoneField.setAccessible(true);
            sPhoneField.set(null, mockPhone);

            Field madeDefaultsField = phoneFactoryClass.getDeclaredField("sMadeDefaults");
            madeDefaultsField.setAccessible(true);
            madeDefaultsField.set(null, true);
        } catch (Throwable e) {
            // Ignore if PhoneFactory or its fields are not accessible
        }

        Context context = ApplicationProvider.getApplicationContext();
        ShadowPackageManager shadowPackageManager = Shadows.shadowOf(context.getPackageManager());

        // Register our test voicemail provider
        ProviderInfo info = new ProviderInfo();
        info.authority = "com.android.voicemail";
        Robolectric.buildContentProvider(TestVoicemailProvider.class).create(info);

        mmsService = Robolectric.setupService(TestMmsService.class);
        final Intent intent = new Intent();
        binder = (IMms.Stub) mmsService.onBind(intent);
    }

    @Test
    public void testOnBind_IsNotNull() {
        assertThat(binder).isNotNull();
    }

    @Test
    public void testSendMessage_DoesNotThrowIfSystemUid() throws RemoteException {
        ShadowBinder.setCallingUid(Process.SYSTEM_UID);
        binder.sendMessage(/* subId= */ 0, /* callingUser= */ CALLING_USER,
                "callingPkg", Uri.parse("contentUri"),
                "locationUrl", /* configOverrides= */ null, /* sentIntent= */ null,
                /* messageId= */ 0L, /* attributionTag= */ null);
    }

    @Test
    public void testSendMessageThrows_IfNotSystemUid() {
        assertThrows(SecurityException.class,
                () -> binder.sendMessage(/* subId= */ 0, /* callingUser= */ CALLING_USER,
                        "callingPkg", Uri.parse("contentUri"),
                        "locationUrl", /* configOverrides= */ null, /* sentIntent= */ null,
                        /* messageId= */ 0L, /* attributionTag= */ null));
    }

    @Test
    public void testReadPduFromContentUri_unauthorizedVoicemailUri_blocked() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        ShadowPackageManager shadowPackageManager = Shadows.shadowOf(context.getPackageManager());

        // Register an app with NO voicemail permissions
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.packageName = "unauthorized.package";
        packageInfo.applicationInfo = new ApplicationInfo();
        packageInfo.applicationInfo.uid = 10002;
        shadowPackageManager.installPackage(packageInfo);

        TestVoicemailProvider.sSourcePackage = "other.package";
        TestVoicemailProvider.sPfd = null;

        Uri voicemailUri = Uri.parse("content://com.android.voicemail/voicemail/999");
        byte[] result = mmsService.readPduFromContentUri(
                voicemailUri,
                /* maxSize= */ 1000,
                UserHandle.myUserId(),
                "unauthorized.package");

        assertThat(result).isNull();
    }

    @Test
    public void testReadPduFromContentUri_authorizedVoicemailUriWithGlobalPermission_allowed() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        ShadowPackageManager shadowPackageManager = Shadows.shadowOf(context.getPackageManager());

        // Register an app with READ_VOICEMAIL permission
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.packageName = "global.package";
        packageInfo.applicationInfo = new ApplicationInfo();
        packageInfo.applicationInfo.uid = 10003;
        packageInfo.requestedPermissions = new String[] { Manifest.permission.READ_VOICEMAIL };
        shadowPackageManager.installPackage(packageInfo);

        // Setup a dummy PDU file
        File tempFile = File.createTempFile("test_pdu", ".bin");
        tempFile.deleteOnExit();
        byte[] expectedPdu = new byte[] { 1, 2, 3 };
        try (FileOutputStream out = new FileOutputStream(tempFile)) {
            out.write(expectedPdu);
        }
        TestVoicemailProvider.sPfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY);
        TestVoicemailProvider.sSourcePackage = "other.package";

        Uri voicemailUri = Uri.parse("content://com.android.voicemail/voicemail/999");
        byte[] result = mmsService.readPduFromContentUri(
                voicemailUri,
                /* maxSize= */ 1000,
                UserHandle.myUserId(),
                "global.package");

        assertThat(result).isEqualTo(expectedPdu);
    }

    @Test
    public void testReadPduFromContentUri_authorizedVoicemailUriWithOwnerPkg_allowed() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        ShadowPackageManager shadowPackageManager = Shadows.shadowOf(context.getPackageManager());

        // Register an app with NO permissions
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.packageName = "owner.package";
        packageInfo.applicationInfo = new ApplicationInfo();
        packageInfo.applicationInfo.uid = 10004;
        shadowPackageManager.installPackage(packageInfo);

        // Setup a dummy PDU file
        File tempFile = File.createTempFile("test_pdu", ".bin");
        tempFile.deleteOnExit();
        byte[] expectedPdu = new byte[] { 4, 5, 6 };
        try (FileOutputStream out = new FileOutputStream(tempFile)) {
            out.write(expectedPdu);
        }
        TestVoicemailProvider.sPfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY);
        TestVoicemailProvider.sSourcePackage = "owner.package"; // Caller matches the source of voicemail

        Uri voicemailUri = Uri.parse("content://com.android.voicemail/voicemail/999");
        byte[] result = mmsService.readPduFromContentUri(
                voicemailUri,
                /* maxSize= */ 1000,
                UserHandle.myUserId(),
                "owner.package");

        assertThat(result).isEqualTo(expectedPdu);
    }
}
