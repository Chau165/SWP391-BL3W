package utils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryNotificationService {

    public static class NotiItem {
        public int id;
        public String title;
        public String content;
        public String linkUrl;
        public long createdAt;
        public boolean read;

        public NotiItem(int id, String title, String content, String linkUrl) {
            this.id = id;
            this.title = title;
            this.content = content;
            this.linkUrl = linkUrl;
            this.createdAt = System.currentTimeMillis();
            this.read = false;
        }
    }

    // userId -> list notifications
    private static final Map<Integer, List<NotiItem>> USER_NOTIS = new ConcurrentHashMap<>();
    private static int NEXT_ID = 1;

    public static void addNotification(int userId, String title, String content, String linkUrl) {
        NotiItem item = new NotiItem(NEXT_ID++, title, content, linkUrl);
        USER_NOTIS.computeIfAbsent(userId, k -> new ArrayList<>())
                  .add(0, item); // add lên đầu
    }

    public static List<NotiItem> getNotifications(int userId) {
        return new ArrayList<>(USER_NOTIS.getOrDefault(userId, Collections.emptyList()));
    }

    public static void markAllRead(int userId) {
        List<NotiItem> list = USER_NOTIS.get(userId);
        if (list != null) {
            for (NotiItem n : list) {
                n.read = true;
            }
        }
    }
}
