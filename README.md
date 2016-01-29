# MessageParser

This is a simple message parser Android app.

It takes a chat message string and returns a JSON string containing information about its contents. Special content to look for includes:
1. @mentions - A way to mention a user. Always starts with an '@' and ends when hitting a non-word character.
2. Emoticons - For this exercise, you only need to consider 'custom' emoticons which are alphanumeric strings, no longer than 15 characters, contained in parenthesis. You can assume that anything matching this format is an emoticon.
3. Links - Any URLs contained in the message, along with the page's title.
