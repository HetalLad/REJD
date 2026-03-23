package rejd_csci;

import java.io.InputStream;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.part.ViewPart;

public class DiagramView extends ViewPart
{
	private Canvas canvas;
	private Label  label;
	private Image image;
	
	public DiagramView()
	{
		super();
	}
	
	public void setFocus()
	{
		canvas.setFocus();
	}
	
	public void createPartControl(Composite parent) {
        canvas = new Canvas(parent, SWT.NONE);

        try {
            InputStream stream = getClass().getResourceAsStream("/somewhiteimage.jpeg");

            if (stream != null) {
                image = new Image(parent.getDisplay(), stream);
            } else {
                System.out.println("Image NOT found");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        canvas.addPaintListener(new PaintListener() {
            @Override
            public void paintControl(PaintEvent e) {
                if (image == null) {
                    return;
                }

                Rectangle imageBounds = image.getBounds();
                Rectangle canvasBounds = canvas.getBounds();

                e.gc.drawImage(
                    image,
                    0, 0, imageBounds.width, imageBounds.height,          // source
                    0, 0, canvasBounds.width, canvasBounds.height         // destination
                );
            }
        });
    }

}
