package com.joooonho.atlassian.messageparser;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.text.Spannable;
import android.text.TextUtils;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.util.Log;
import android.util.Patterns;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utility {
    private static final String TAG = Utility.class.getSimpleName();

    private static final int TIME_OUT = 3000;

    private static void gatherLinks(ArrayList<LinkSpec> links,
                                    Spannable s, Pattern pattern, String[] schemes,
                                    Linkify.MatchFilter matchFilter) {
        Matcher m = pattern.matcher(s);
        while (m.find()) {
            int start = m.start();
            int end = m.end();
            if (matchFilter == null || matchFilter.acceptMatch(s, start, end)) {
                LinkSpec spec = new LinkSpec();
                spec.url = makeUrl(m.group(0), schemes);
                spec.start = start;
                spec.end = end;
                links.add(spec);
            }
        }
    }

    public static boolean hasInternetConnection(Context context) {
        ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
    }

    public static boolean isValidEmoticon(String s) {
        if (TextUtils.isEmpty(s) || s.length() > 15) {
            return false;
        }

        for (int i = 0; i < s.length(); i++) {
            if (!Character.isLetterOrDigit(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public static List<String> parseLinks(Spannable text) {
        URLSpan[] old = text.getSpans(0, text.length(), URLSpan.class);
        for (int i = old.length - 1; i >= 0; i--) {
            text.removeSpan(old[i]);
        }

        ArrayList<LinkSpec> links = new ArrayList<>();
        gatherLinks(links, text, Patterns.WEB_URL,
                new String[]{"http://", "https://", "rtsp://"},
                Linkify.sUrlMatchFilter);
        pruneOverlaps(links);

        ArrayList<String> linkStrings = new ArrayList<>(links.size());
        for (LinkSpec link : links) {
            linkStrings.add(link.url);
        }
        return linkStrings;
    }

    private static String makeUrl(String url, String[] prefixes) {
        boolean hasPrefix = false;

        for (String prefix : prefixes) {
            if (url.regionMatches(true, 0, prefix, 0, prefix.length())) {
                hasPrefix = true;
                // Fix capitalization if necessary
                if (!url.regionMatches(false, 0, prefix, 0,
                        prefix.length())) {
                    url = prefix + url.substring(prefix.length());
                }
                break;
            }
        }
        if (!hasPrefix) {
            url = prefixes[0] + url;
        }
        return url;
    }

    private static void pruneOverlaps(ArrayList<LinkSpec> links) {
        Comparator<LinkSpec> c = new Comparator<LinkSpec>() {
            public final int compare(LinkSpec a, LinkSpec b) {
                if (a.start < b.start) {
                    return -1;
                }
                if (a.start > b.start) {
                    return 1;
                }
                if (a.end < b.end) {
                    return 1;
                }
                if (a.end > b.end) {
                    return -1;
                }
                return 0;
            }
        };
        Collections.sort(links, c);
        int len = links.size();
        int i = 0;
        while (i < len - 1) {
            LinkSpec a = links.get(i);
            LinkSpec b = links.get(i + 1);
            int remove = -1;
            if ((a.start <= b.start) && (a.end > b.start)) {
                if (b.end <= a.end) {
                    remove = i + 1;
                } else if ((a.end - a.start) > (b.end - b.start)) {
                    remove = i + 1;
                } else if ((a.end - a.start) < (b.end - b.start)) {
                    remove = i;
                }
                if (remove != -1) {
                    links.remove(remove);
                    len--;
                    continue;
                }
            }
            i++;
        }
    }

    public static String getTitleFromUrl (String url) {
        String title = "";
        try {
            Document doc = Jsoup.connect(url).timeout(TIME_OUT).get();
            title = doc.title();
        } catch (IOException e) {
            Log.e(TAG, "IOException has occurred in getTitleFromUrl()! " + e.getMessage());
        }
        return title;
    }

    private static class LinkSpec {
        String url;
        int start;
        int end;
    }
}
