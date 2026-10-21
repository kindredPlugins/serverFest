package at.gaderman.serverFest.utils;

public class ChatUtils {

    public static String toPlayerFriendlyName(String input) {
        StringBuilder builder = new StringBuilder();

        boolean bigLetter = true;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (c == '_') {
                bigLetter = true;
                builder.append(' ');
                continue;
            }

            if (bigLetter) {
                builder.append((c + "").toUpperCase());
                bigLetter = false;
            } else {
                builder.append((c + "").toLowerCase());
            }
        }

        return builder.toString();
    }

}
