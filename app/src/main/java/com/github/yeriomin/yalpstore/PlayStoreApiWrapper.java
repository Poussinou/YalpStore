package com.github.yeriomin.yalpstore;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;

import com.github.yeriomin.playstoreapi.AndroidAppDeliveryData;
import com.github.yeriomin.playstoreapi.AppDetails;
import com.github.yeriomin.playstoreapi.BulkDetailsEntry;
import com.github.yeriomin.playstoreapi.BuyResponse;
import com.github.yeriomin.playstoreapi.DocV2;
import com.github.yeriomin.playstoreapi.GooglePlayAPI;
import com.github.yeriomin.playstoreapi.HttpCookie;
import com.github.yeriomin.playstoreapi.Image;
import com.github.yeriomin.playstoreapi.SearchResponse;
import com.github.yeriomin.playstoreapi.SearchSuggestEntry;
import com.github.yeriomin.yalpstore.model.App;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static android.content.Context.DOWNLOAD_SERVICE;

/**
 * Akdeniz Google Play Crawler classes are supposed to be independent from android,
 * so this warpper manages anything android-related and feeds it to the Akdeniz's classes
 * Specifically: credentials via Preferences, downloads via DownloadManager, app details using
 * android PackageInfo
 */
public class PlayStoreApiWrapper {

    private Context context;
    private String email;
    private String password;

    private static GooglePlayAPI api;
    private static AppSearchResultIterator searchResultIterator;

    private App buildApp(DocV2 details) {
        App app = new App();
        app.setDisplayName(details.getTitle());
        app.setDescription(details.getDescriptionHtml());
        app.setRating(details.getAggregateRating().getStarRating());
        if (details.getOfferCount() > 0) {
            app.setOfferType(details.getOffer(0).getOfferType());
            app.setFree(details.getOffer(0).getMicros() == 0);
        }
        AppDetails appDetails = details.getDetails().getAppDetails();
        app.getPackageInfo().packageName = appDetails.getPackageName();
        app.setVersionName(appDetails.getVersionString());
        app.setVersionCode(appDetails.getVersionCode());
        app.setSize(appDetails.getInstallationSize());
        Pattern pattern = Pattern.compile("[ ,>\\.\\+\\d\\s]+");
        Matcher matcher = pattern.matcher(appDetails.getNumDownloads());
        if (matcher.find()) {
            String installs = matcher.group(0)
                .replaceAll("[\\s\\.,]000[\\s\\.,]000[\\s\\.,]000", context.getString(R.string.suffix_billion))
                .replaceAll("[\\s\\.,]000[\\s\\.,]000", context.getString(R.string.suffix_million))
                ;
            app.setInstalls(installs);
        }
        app.setUpdated(appDetails.getUploadDate());
        Image iconImage = null;
        for (Image image: details.getImageList()) {
            if (image.getImageType() == 4) {
                iconImage = image;
                break;
            }
        }
        if (iconImage != null) {
            app.setIconUrl(iconImage.getImageUrl());
        }
        app.setChanges(appDetails.getRecentChangesHtml());
        app.getDeveloper().setName(appDetails.getDeveloperName());
        app.getDeveloper().setEmail(appDetails.getDeveloperEmail());
        app.getDeveloper().setWebsite(appDetails.getDeveloperWebsite());
        app.setPermissions(appDetails.getPermissionList());
        return app;
    }

    private GooglePlayAPI getApi() throws IOException {
        if (api == null) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            String email = this.email == null ? prefs.getString(PreferenceActivity.PREFERENCE_EMAIL, "") : this.email;
            String gsfId = prefs.getString(PreferenceActivity.PREFERENCE_GSF_ID, "");
            String token = prefs.getString(PreferenceActivity.PREFERENCE_AUTH_TOKEN, "");
            if (email.isEmpty() || (token.isEmpty() && password == null)) {
                throw new CredentialsEmptyException();
            }

            NativeDeviceInfoProvider checkinRequestBuilder = new NativeDeviceInfoProvider();
            checkinRequestBuilder.setContext(context);
            checkinRequestBuilder.setLocaleString(Locale.getDefault().toString());
            api = new GooglePlayAPI(email);
            api.setDeviceInfoProvider(checkinRequestBuilder);
            api.setLocale(Locale.getDefault());
            SharedPreferences.Editor prefsEditor = prefs.edit();

            boolean needToUploadDeviceConfig = false;
            if (gsfId.isEmpty()) {
                needToUploadDeviceConfig = true;
                gsfId = api.getGsfId(password);
                prefsEditor.putString(PreferenceActivity.PREFERENCE_GSF_ID, gsfId);
                prefsEditor.apply();
            }
            api.setGsfId(gsfId);
            if (token.isEmpty()) {
                token = api.getToken(password);
                prefsEditor.putString(PreferenceActivity.PREFERENCE_EMAIL, email);
                prefsEditor.putString(PreferenceActivity.PREFERENCE_AUTH_TOKEN, token);
                prefsEditor.apply();
            }
            api.setToken(token);
            if (needToUploadDeviceConfig) {
                try {
                    api.uploadDeviceConfig();
                } catch (IOException e) {
                    // Its fine if this fails
                    Log.e(this.getClass().getName(), e.getClass().toString() + ": " + e.getMessage());
                }
            }
        }
        return api;
    }

    public PlayStoreApiWrapper(Context context) {
        this.context = context;
    }

    public GooglePlayAPI login(String email, String password) throws IOException {
        this.email = email;
        this.password = password;
        PlayStoreApiWrapper.api = null;
        return getApi();
    }

    public void logout() {
        this.email = null;
        this.password = null;
        SharedPreferences.Editor prefs = PreferenceManager.getDefaultSharedPreferences(context).edit();
        prefs.remove(PreferenceActivity.PREFERENCE_GSF_ID);
        prefs.remove(PreferenceActivity.PREFERENCE_AUTH_TOKEN);
        prefs.apply();
        PlayStoreApiWrapper.api = null;
    }

    public void forceTokenRefresh() {
        this.password = null;
        SharedPreferences.Editor prefs = PreferenceManager.getDefaultSharedPreferences(context).edit();
        prefs.remove(PreferenceActivity.PREFERENCE_AUTH_TOKEN);
        prefs.apply();
        PlayStoreApiWrapper.api = null;
    }

    public App getDetails(String packageId) throws IOException {
        return buildApp(getApi().details(packageId).getDocV2());
    }

    public List<App> getDetails(List<String> packageIds) throws IOException {
        List<App> apps = new ArrayList<>();
        int i = 0;
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        boolean hideNonFree = sharedPreferences.getBoolean(PreferenceActivity.PREFERENCE_HIDE_NONFREE_APPS, false);
        for (BulkDetailsEntry details: getApi().bulkDetails(packageIds).getEntryList()) {
            if (details.hasDoc()) {
                App app = buildApp(details.getDoc());
                if (hideNonFree && !app.isFree()) {
                    Log.i(this.getClass().getName(), "Skipping non-free app " + packageIds.get(i));
                } else {
                    apps.add(app);
                }
            } else {
                Log.i(this.getClass().getName(), "Empty response for " + packageIds.get(i));
            }
            i++;
        }
        Collections.sort(apps, new Comparator<App>() {
            @Override
            public int compare(App o1, App o2) {
                return o1.getDisplayName().compareToIgnoreCase(o2.getDisplayName());
            }
        });
        return apps;
    }

    public AppSearchResultIterator getSearchIterator(String query) throws IOException {
        if (null == query || query.isEmpty()) {
            Log.w(this.getClass().getName(), "Query empty, so don't expect meaningful results");
        }
        if (null == searchResultIterator || query != searchResultIterator.getQuery()) {
            searchResultIterator = new AppSearchResultIterator(getApi().getSearchIterator(query));
        }
        return searchResultIterator;
    }

    public List<String> getSearchSuggestions(String query) throws IOException {
        List<String> suggestions = new ArrayList<>();
        for (SearchSuggestEntry suggestion: api.searchSuggest(query).getEntryList()) {
            suggestions.add(suggestion.getSuggestedQuery());
        }
        return suggestions;
    }

    public void download(App app) throws IOException {
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        String localFilename = app.getPackageName() + "." + String.valueOf(app.getVersionCode()) + ".apk";
        Uri uri = Uri.withAppendedPath(Uri.fromFile(downloadsDir), localFilename);

        if (new File(uri.getPath()).exists()) {
            Log.i(this.getClass().getName(), localFilename + " exists. No download needed.");
            context.startActivity(getOpenApkIntent(uri));
        } else {
            Log.i(this.getClass().getName(), "Downloading apk to " + localFilename);
            BuyResponse response = getApi().purchase(app.getPackageName(), app.getVersionCode(), app.getOfferType());
            AndroidAppDeliveryData appDeliveryData = response.getPurchaseStatusResponse().getAppDeliveryData();

            // Download manager cannot download https on old android versions
            String downloadUrl = appDeliveryData.getDownloadUrl().replace("https", "http");
            HttpCookie downloadAuthCookie = appDeliveryData.getDownloadAuthCookie(0);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(downloadUrl));
            request.addRequestHeader("Cookie", downloadAuthCookie.getName() + "=" + downloadAuthCookie.getValue());
            request.setDestinationUri(uri);
            request.setTitle(app.getDisplayName());

            ((DownloadManager) this.context.getSystemService(DOWNLOAD_SERVICE)).enqueue(request);
        }
    }

    static public Intent getOpenApkIntent(Uri uri) {
        Intent openIntent = new Intent();
        openIntent.setAction(Intent.ACTION_VIEW);
        openIntent.setDataAndType(uri, "application/vnd.android.package-archive");
        return openIntent;
    }

    class AppSearchResultIterator implements Iterator<List<App>> {

        private GooglePlayAPI.SearchIterator iterator;

        public AppSearchResultIterator(GooglePlayAPI.SearchIterator iterator) {
            this.iterator = iterator;
        }

        public String getQuery() {
            return this.iterator.getQuery();
        }

        @Override
        public List<App> next() {
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
            boolean hideNonFree = sharedPreferences.getBoolean(PreferenceActivity.PREFERENCE_HIDE_NONFREE_APPS, false);
            List<App> apps = new ArrayList<>();
            SearchResponse response = iterator.next();
            if (response.getDocCount() > 0) {
                for (DocV2 details: response.getDocList().get(0).getChildList()) {
                    App app = buildApp(details);
                    if (hideNonFree && !app.isFree()) {
                        Log.i(this.getClass().getName(), "Skipping non-free app " + app.getPackageName());
                    } else {
                        apps.add(app);
                    }
                }
            }
            return apps;
        }

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }
    }

}
