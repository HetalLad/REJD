/*
File name: PositionModel.java
Authors: Anirvinna Jain, Hetal Lad, Saptorshee Nag 
Description: The PositionModel class is intialized here
*/

// package info
package gwu.rejd.notes;

// import statements
import java.util.ArrayList;
import java.util.List;

public class PositionModel {
    public String componentId;
    public double x;
    public double y;

    public PositionModel() {}

    public PositionModel(String componentId, double x, double y)
    {
        this.componentId = componentId;
        this.x = x;
        this.y = y;
    }
}
