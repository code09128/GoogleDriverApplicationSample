package com.drs24.googledriver;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.Scope;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.FileContent;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import java.util.Collections;

/**
 * Created by dustin0128 on 2019/05/30.
 */
public class GoogleDriveUtil {
    /*雲端硬碟使用者ID(帳號omnihealth.24drs註冊)*/
    public static final String GOOGLE_DRIVE_ID = "332621158758-fp7vctsgrc926gs8bvtgs5t6ktepq7kf.apps.googleusercontent.com";

    private final static String TAG = GoogleDriveUtil.class.getSimpleName();
    private static GoogleDriveUtil googleDriveUtil;
    private GoogleDriveCallbackListeners callbackListeners;
    private Drive driveService;

    public interface GoogleDriveCallbackListeners {
        void requestSignInResult(String email);

        void addFileResult(String id);

        void addFolderResult(String id);

        void insertingFileInFolderResult(String id);

        void movingFilesBetweenFoldersResult(String id);
    }

    public void setGoogleDriveCallbackListeners(GoogleDriveCallbackListeners callbackListeners) {
        this.callbackListeners = callbackListeners;
    }

    /** Google Drive模組*/
    public static GoogleDriveUtil getGoogleDriveUtil() {
        if (googleDriveUtil == null) {
            googleDriveUtil = new GoogleDriveUtil();
        }

        return googleDriveUtil;
    }

    /**請求登入驗證*/
    public GoogleSignInClient requestSignIn(Context context) {
        GoogleSignInOptions signInOptions = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(new Scope(DriveScopes.DRIVE))
                .requestIdToken(GOOGLE_DRIVE_ID)
                .build();

        return GoogleSignIn.getClient(context, signInOptions);
    }

    /*Google帳號登出*/
    private void requestSignOut(Context context) {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(GOOGLE_DRIVE_ID)
                .requestEmail()
                .build();

        GoogleSignIn.getClient(context, gso).signOut().addOnCompleteListener(task ->
                Toast.makeText(context, "SingOut", Toast.LENGTH_LONG).show());
    }

    /**登入驗證回調並取得帳號資訊識別已登入之狀態*/
    public void signedInAccountFromIntent(Context context, Intent result) {
        GoogleSignIn.getSignedInAccountFromIntent(result)
                .addOnSuccessListener(googleAccount -> {
                    Log.d(TAG, "Signed in as " + googleAccount.getEmail());

                    GoogleAccountCredential credential =
                            GoogleAccountCredential.usingOAuth2(context, Collections.singleton(DriveScopes.DRIVE));

                    credential.setSelectedAccount(googleAccount.getAccount());

                    driveService = new Drive.Builder(
                            AndroidHttp.newCompatibleTransport(),
                            new GsonFactory(),
                            credential)
                            .setApplicationName("Lumify")
                            .build();

                    callbackListeners.requestSignInResult(googleAccount.getEmail());
                    setDriveService(driveService);
                }).addOnFailureListener(exception -> Log.e(TAG, "Unable to sign in.", exception));
    }

    /**上傳檔案*/
    public void addFile(String pathname, String fileName) {
        new Thread(() -> {
            try {
                java.io.File dir = new java.io.File(pathname);
                java.io.File filePath = new java.io.File(dir, fileName);

                File fileMetadata = new File();
                fileMetadata.setName(fileName);

                FileContent mediaContent = new FileContent("", filePath);
                File file = getDriveService().files().create(fileMetadata, mediaContent)
                            .setFields("id")
                            .execute();

                callbackListeners.addFileResult(file.getId());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    /**新增文件夾*/
    public void addFolder(String folderName) {
        new Thread(() -> {
            try {
                File fileMetadata = new File();
                fileMetadata.setName(folderName);
                fileMetadata.setMimeType("application/vnd.google-apps.folder");

                File file = getDriveService().files().create(fileMetadata)
                        .setFields("id")
                        .execute();

                callbackListeners.addFolderResult(file.getId());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    /**文件夾裡新增檔案*/
    public void insertingFileInFolder(String folderId, String pathname, String fileName) {
        new Thread(() -> {
            try {
                java.io.File dir = new java.io.File(pathname);
                java.io.File filePath = new java.io.File(dir, fileName);

                File fileMetadata = new File();
                fileMetadata.setName(pathname);
                fileMetadata.setParents(Collections.singletonList(folderId));

                FileContent mediaContent = new FileContent("", filePath);
                File file = getDriveService().files().create(fileMetadata, mediaContent)
                        .setFields("id, parents")
                        .execute();

                callbackListeners.insertingFileInFolderResult(file.getId());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    /**檔案移至新的文件夾*/
    public void movingFilesBetweenFolders(String folderId, String fileId) {
        new Thread(() -> {
            try {
                //檢查需刪除之項目
                File file = getDriveService().files().get(fileId)
                        .setFields("parents")
                        .execute();

                StringBuilder previousParents = new StringBuilder();
                for (String parent : file.getParents()) {
                    previousParents.append(parent);
                    previousParents.append(',');
                }

                //將檔案移至新的文件夾
                file = getDriveService().files().update(fileId, null)
                        .setAddParents(folderId)
                        .setRemoveParents(previousParents.toString())
                        .setFields("id, parents")
                        .execute();

                callbackListeners.movingFilesBetweenFoldersResult(file.getId());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    /**查找Dicom檔案*/
    public void queryDicom() {
        new Thread(() -> {
            try {
                String pageToken = null;
                do {
                    FileList result = driveService.files().list()
                            .setQ("mimeType='application/dicom'")
                            .setSpaces("drive")
                            .setPageToken(pageToken)
                            .execute();
                    for (File file : result.getFiles()) {
                        System.out.printf("Found file: %s (%s)\n", file.getName(), file.getId());
                    }
                    pageToken = result.getNextPageToken();
                } while (pageToken != null);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    /**取得Google硬碟服務*/
    private Drive getDriveService() {
        return driveService;
    }

    /**設置Google硬碟服務*/
    private void setDriveService(Drive driveService) {
        this.driveService = driveService;
    }
}