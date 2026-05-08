package gwu.rejd.notes;

import java.util.ArrayList;
import java.util.List;

public class ReplyModel {
    public String id;
    public String author;
    public String timestamp;
    public String text;
    public boolean isDeleted;

    // Nested replies / replies-to-replies
    public List<ReplyModel> replies = new ArrayList<>();
}