package gwu.rejd.notes;

import java.util.ArrayList;
import java.util.List;

public class NoteModel {
    public String id;
    public String componentId;
    public String author;
    public String timestamp;
    public String text;
    public boolean isDeleted;

    // Used for general canvas notes
    public Double x;
    public Double y;

    public List<ReplyModel> replies = new ArrayList<>();
}