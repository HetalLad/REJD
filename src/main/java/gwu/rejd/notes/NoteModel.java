/*
File name: NoteModel.java
Authors: Anirvinna Jain, Hetal Lad, Saptorshee Nag 
Description: The NoteModel class is intialized here
*/

// Package info
package gwu.rejd.notes;

// Imports
import java.util.ArrayList;
import java.util.List;

/*
Class NoteModel initializes the NoteModel object.
*/
public class NoteModel {
    public String id;  
    public String componentId;
    public String author;
    public String timestamp;
    public String text;
    public boolean isDeleted;

    public Double x;
    public Double y;

    public List<ReplyModel> replies = new ArrayList<>();
}
