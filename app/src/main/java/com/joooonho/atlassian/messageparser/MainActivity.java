package com.joooonho.atlassian.messageparser;

import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.MultiAutoCompleteTextView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = MainActivity.class.getSimpleName();

    private TextView mTextView;
    // TODO remove mentioned user from auto completion list
    private MultiAutoCompleteTextView mMultiAutoCompText;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTextView = (TextView) findViewById(R.id.text);

        String [] hipchatters = getResources().getStringArray(R.array.hipchatters);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, hipchatters);
        mMultiAutoCompText = (MultiAutoCompleteTextView) findViewById(R.id.edit);
        mMultiAutoCompText.setAdapter(adapter);
        mMultiAutoCompText.setTokenizer(new MentionTokenizer());
        mMultiAutoCompText.setThreshold(1);

        Button button = (Button) findViewById(R.id.button);
        button.setOnClickListener(this);
    }

    public JSONObject parse(CharSequence str) {
        if (TextUtils.isEmpty(str)) {
            return new JSONObject();
        }

        List<String> mentionList = parseMentions(str.toString());
        List<String> emoList = parseEmos(str.toString());
        List<String> linkStrings = Utility.parseLinks((Spannable) str);
        List<LinkContent> linkList = getLinkTitles(linkStrings);
        return combine(mentionList, emoList, linkList);
    }

    private List<LinkContent> getLinkTitles(List<String> linkStrings) {
        List<LinkContent> result = new LinkedList<>();
        if (Utility.hasInternetConnection(this)) {
            for (String link : linkStrings) {
                result.add(new LinkContent(link, Utility.getTitleFromUrl(link)));
            }
        } else {
            for (String link : linkStrings) {
                result.add(new LinkContent(link, ""));
            }
        }
        return result;
    }

    private JSONObject combine(List<String> mentionList, List<String> emoList, List<LinkContent> linkList) {
        JSONObject jObj = new JSONObject();
        try {
            if (!mentionList.isEmpty()) {
                JSONArray array = new JSONArray();
                for (String mention : mentionList) {
                    array.put(mention);
                }
                jObj.put("mentions", array);
            }

            if (!emoList.isEmpty()) {
                JSONArray array = new JSONArray();
                for (String emoticon : emoList) {
                    array.put(emoticon);
                }
                jObj.put("emoticons", array);
            }

            if (!linkList.isEmpty()) {
                JSONArray array = new JSONArray();
                for (LinkContent content : linkList) {
                    JSONObject obj = new JSONObject();
                    obj.put("url", content.url);
                    obj.put("title", content.title);
                    array.put(obj);
                }
                jObj.put("links", array);
            }
        } catch (JSONException e) {
            // should never happen
            Log.e(TAG, "Oops, JSONException has occurred! error : " + e.getMessage()) ;
        }
        return jObj;
    }

    private List<String> parseMentions(String s) {
        List<String> mentionList = new LinkedList<>();
        while (s.contains("@")) {
            int beginIndex = s.indexOf("@");
            int endIndex = beginIndex;
            if (beginIndex + 1 < s.length() && Character.isLetterOrDigit(s.charAt(beginIndex + 1))) {
                // user name should be at least 1 length long word
                for (endIndex = beginIndex + 1; endIndex < s.length(); endIndex++) {
                    if (!Character.isLetterOrDigit(s.charAt(endIndex))) {
                        break;
                    }
                }
                mentionList.add(s.substring(beginIndex + 1, endIndex));
            }

            String after = "";
            if (endIndex < s.length()) {
                after = s.substring(endIndex + 1);
            }
            s = s.substring(0, beginIndex) + after;
        }
        return mentionList;
    }

    private List<String> parseEmos(String s) {
        List<String> emoList = new LinkedList<>();
        outer:
        while (s.contains("(") && s.contains(")")) {
            int innerMostOpenIndex = -1;
            for (int i = 0; i < s.length(); i++) {
                if (s.charAt(i) == '(') {
                    innerMostOpenIndex = i;
                } else if (s.charAt(i) == ')' && innerMostOpenIndex != -1) {
                    String sub = s.substring(innerMostOpenIndex + 1, i);
                    if (Utility.isValidEmoticon(sub)) {
                        emoList.add(sub);
                    }
                    s = s.substring(0, innerMostOpenIndex) + s.substring(i + 1);
                    continue outer;
                }
            }
            break;
        }
        return emoList;
    }

    @Override
    public void onClick(View v) {
        if (!TextUtils.isEmpty(mMultiAutoCompText.getText())) {
            SpannableString ss = new SpannableString(mMultiAutoCompText.getText());
            new ParseTask().execute(ss);
            mMultiAutoCompText.getText().clear();
        }
    }

    private static class LinkContent {
        String url;
        String title;

        LinkContent (String u, String t) {
            url = u;
            title = t;
        }
    }

    private class ParseTask extends AsyncTask<CharSequence, Void, JSONObject> {
        @Override
        protected JSONObject doInBackground(CharSequence... params) {
            return parse(params[0]);
        }

        protected void onPostExecute(JSONObject result) {
            String s = "";
            try {
                s = result.toString(4);
            } catch (JSONException e) {
                // ignore it.
            }
            mTextView.setText(s);
        }
    }

    public static class MentionTokenizer implements MultiAutoCompleteTextView.Tokenizer {
        public int findTokenStart(CharSequence text, int cursor) {
            int i = cursor;
            while (i > 0) {
                if (text.charAt(i - 1) == '@') {
                    return i - 1;
                }
                i--;
            }
            return i;
        }

        public int findTokenEnd(CharSequence text, int cursor) {
            int i = cursor;
            int len = text.length();
            while (i < len) {
                if (!Character.isLetterOrDigit(text.charAt(i))) {
                    return i;
                } else {
                    i++;
                }
            }
            return len;
        }

        public CharSequence terminateToken(CharSequence text) {
            int i = text.length();
            if (i > 0 && !Character.isLetterOrDigit(text.charAt(i - 1))) {
                return text;
            } else {
                if (text instanceof Spanned) {
                    SpannableString sp = new SpannableString(text + " ");
                    TextUtils.copySpansFrom((Spanned) text, 0, text.length(),
                            Object.class, sp, 0);
                    return sp;
                } else {
                    return text + " ";
                }
            }
        }
    }
}
