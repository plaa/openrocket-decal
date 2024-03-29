package net.sf.openrocket.gui.figure3d;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLCapabilities;
import javax.media.opengl.GLEventListener;
import javax.media.opengl.GLProfile;
import javax.media.opengl.awt.GLCanvas;
import javax.media.opengl.fixedfunc.GLLightingFunc;
import javax.media.opengl.fixedfunc.GLMatrixFunc;
import javax.media.opengl.glu.GLU;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.event.MouseInputAdapter;

import net.sf.openrocket.document.OpenRocketDocument;
import net.sf.openrocket.gui.figureelements.CGCaret;
import net.sf.openrocket.gui.figureelements.CPCaret;
import net.sf.openrocket.gui.figureelements.FigureElement;
import net.sf.openrocket.logging.LogHelper;
import net.sf.openrocket.rocketcomponent.Configuration;
import net.sf.openrocket.rocketcomponent.RocketComponent;
import net.sf.openrocket.startup.Application;
import net.sf.openrocket.util.Coordinate;
import net.sf.openrocket.util.MathUtil;

import com.jogamp.opengl.util.awt.Overlay;

/*
 * @author Bill Kuker <bkuker@billkuker.com>
 */
public class RocketFigure3d extends JPanel implements GLEventListener {
	
	public static final int TYPE_REALISTIC = 0;
	public static final int TYPE_FIGURE = 1;
	
	private static final long serialVersionUID = 1L;
	private static final LogHelper log = Application.getLogger();
	
	static {
		//this allows the GL canvas and things like the motor selection
		//drop down to z-order themselves.
		JPopupMenu.setDefaultLightWeightPopupEnabled(false);
	}

	private static final double fovY = 15.0;
	private static double fovX = Double.NaN;
	private static final int CARET_SIZE = 20;
	
	private OpenRocketDocument document;
	private Configuration configuration;
	private GLCanvas canvas;


	
	private Overlay extrasOverlay, caretOverlay;
	private BufferedImage cgCaretRaster, cpCaretRaster;
	private volatile boolean redrawExtras = true;

	private final ArrayList<FigureElement> relativeExtra = new ArrayList<FigureElement>();
	private final ArrayList<FigureElement> absoluteExtra = new ArrayList<FigureElement>();

	private double roll = 0;
	private double yaw = 0;

	Point pickPoint = null;
	MouseEvent pickEvent;

	float[] lightPosition = new float[] { 1, 4, 1, 0 };

	RocketRenderer rr = new RocketRenderer();

	public RocketFigure3d(OpenRocketDocument document, Configuration config) {
		this.document = document;
		this.configuration = config;
		this.setLayout(new BorderLayout());

		addHierarchyListener(new HierarchyListener() {
			@Override
			public void hierarchyChanged(HierarchyEvent e) {
				initGLCanvas();
				RocketFigure3d.this.removeHierarchyListener(this);
			}
		});
	}
	
	private void initGLCanvas(){
		log.debug("Initializing RocketFigure3D OpenGL Canvas");
		try {
			log.debug("Setting up GL capabilities...");
			GLProfile glp = GLProfile.getDefault();
			GLCapabilities caps = new GLCapabilities(glp);
			caps.setSampleBuffers(true);
			caps.setNumSamples(6);
			caps.setStencilBits(1);

			log.debug("Creating OpenGL Canvas");
			canvas = new GLCanvas(caps);

			canvas.addGLEventListener(this);
			this.add(canvas, BorderLayout.CENTER);

			setupMouseListeners();
			rasterizeCarets();
			
		} catch (Throwable t) {
			log.error("An error occurred creating 3d View", t);
			canvas = null;
			this.add(new JLabel("Unable to load 3d Libraries: "
					+ t.getMessage()));
		}
	}
	
	/**
	 * Set up the standard rendering hints on the Graphics2D
	 */
	private static void setRenderingHints(Graphics2D g){
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
				RenderingHints.VALUE_STROKE_NORMALIZE);
		g.setRenderingHint(RenderingHints.KEY_RENDERING,
				RenderingHints.VALUE_RENDER_QUALITY);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
				RenderingHints.VALUE_ANTIALIAS_ON);
	}
	
	/**
	 * Rasterize the carets into 2 buffered images that I can blit onto the
	 * 3d display every redraw without all of the caret shape rendering overhead
	 */
	private void rasterizeCarets(){
		Graphics2D g2d;
		
		//Rasterize a CG Caret
		cgCaretRaster = new BufferedImage(CARET_SIZE, CARET_SIZE, BufferedImage.TYPE_4BYTE_ABGR);
		g2d = cgCaretRaster.createGraphics();
		setRenderingHints(g2d);
		
		g2d.setBackground(new Color(0, 0, 0, 0));
		g2d.clearRect(0, 0, CARET_SIZE, CARET_SIZE);
		
		new CGCaret(CARET_SIZE/2,CARET_SIZE/2).paint(g2d, 1.0);
		
		g2d.dispose();

		//Rasterize a CP Caret
		cpCaretRaster = new BufferedImage(CARET_SIZE, CARET_SIZE, BufferedImage.TYPE_4BYTE_ABGR);
		g2d = cpCaretRaster.createGraphics();
		setRenderingHints(g2d);
		
		g2d.setBackground(new Color(0, 0, 0, 0));
		g2d.clearRect(0, 0, CARET_SIZE, CARET_SIZE);
		
		new CPCaret(CARET_SIZE/2,CARET_SIZE/2).paint(g2d, 1.0);
		
		g2d.dispose();
		
	}

	private void setupMouseListeners() {
		MouseInputAdapter a = new MouseInputAdapter() {
			int lastX;
			int lastY;
			MouseEvent pressEvent;

			@Override
			public void mousePressed(MouseEvent e) {
				lastX = e.getX();
				lastY = e.getY();
				pressEvent = e;
			}

			@Override
			public void mouseClicked(MouseEvent e) {
				pickPoint = new Point(lastX, canvas.getHeight() - lastY);
				pickEvent = e;
				internalRepaint();
			}

			@Override
			public void mouseDragged(MouseEvent e) {
				int dx = lastX - e.getX();
				int dy = lastY - e.getY();
				lastX = e.getX();
				lastY = e.getY();

				if (pressEvent.getButton() == MouseEvent.BUTTON1) {
					if (Math.abs(dx) > Math.abs(dy)) {
						setYaw(yaw - (float) dx / 100.0);
					} else {
						if ( yaw > Math.PI/2.0 && yaw < 3.0*Math.PI/2.0 ){
							dy = -dy;
						}
						setRoll(roll - (float) dy / 100.0);
					}
				} else {
					lightPosition[0] -= 0.1f * dx;
					lightPosition[1] += 0.1f * dy;
					internalRepaint();
				}
			}
		};
		canvas.addMouseMotionListener(a);
		canvas.addMouseListener(a);
	}

	public void setConfiguration(Configuration configuration) {
		this.configuration = configuration;
		updateFigure();
	}

	@Override
	public void display(GLAutoDrawable drawable) {
		GL2 gl = drawable.getGL().getGL2();
		GLU glu = new GLU();

		gl.glEnable(GL.GL_MULTISAMPLE);

		gl.glClearColor(1, 1, 1, 1);
		gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);

		setupView(gl, glu);

		if (pickPoint != null) {
			gl.glDisable(GLLightingFunc.GL_LIGHTING);
			final RocketComponent picked = rr.pick(drawable, configuration,
					pickPoint, pickEvent.isShiftDown()?selection:null );
			if (csl != null && picked != null) {
				final MouseEvent e = pickEvent;
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						csl.componentClicked(new RocketComponent[] { picked },
								e);
					}
				});

			}
			pickPoint = null;

			gl.glClearColor(1, 1, 1, 1);
			gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);

			gl.glEnable(GLLightingFunc.GL_LIGHTING);
		}
		rr.render(drawable, configuration, selection);
		
		drawExtras(gl, glu);
		drawCarets(gl, glu);
	}

	
	private void drawCarets(GL2 gl, GLU glu) {
		final Graphics2D og2d = caretOverlay.createGraphics();
		setRenderingHints(og2d);
		
		og2d.setBackground(new Color(0, 0, 0, 0));
		og2d.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
		caretOverlay.markDirty(0, 0, canvas.getWidth(), canvas.getHeight());

		// The existing relative Extras don't really work right for 3d.
		Coordinate pCP = project(cp, gl, glu);
		Coordinate pCG = project(cg, gl, glu);

		final int d = CARET_SIZE/2;
		
		//z order the carets 
		if (pCG.z < pCP.z) {
			//Subtract half of the caret size, so they are centered ( The +/- d in each translate)
			//Flip the sense of the Y coordinate from GL to normal (Y+ up/down)
			og2d.drawRenderedImage(
					cpCaretRaster,
					AffineTransform.getTranslateInstance((pCP.x - d),
							canvas.getHeight() - (pCP.y + d)));
			og2d.drawRenderedImage(
					cgCaretRaster,
					AffineTransform.getTranslateInstance((pCG.x - d),
							canvas.getHeight() - (pCG.y + d)));
		} else {
			og2d.drawRenderedImage(
					cgCaretRaster,
					AffineTransform.getTranslateInstance((pCG.x - d),
							canvas.getHeight() - (pCG.y + d)));
			og2d.drawRenderedImage(
					cpCaretRaster,
					AffineTransform.getTranslateInstance((pCP.x - d),
							canvas.getHeight() - (pCP.y + d)));
		}
		og2d.dispose();
		
		gl.glEnable(GL.GL_BLEND);
		caretOverlay.drawAll();
		gl.glDisable(GL.GL_BLEND);
	}
	
	/**
	 * Draw the extras overlay to the gl canvas.
	 * Re-blits the overlay every frame. Only re-renders the overlay
	 * when needed.
	 */
	private void drawExtras(GL2 gl, GLU glu){
		//Only re-render if needed
		//	redrawExtras: Some external change (new simulation data) means
		//		the data is out of date.
		//	extrasOverlay.contentsLost(): For some reason the buffer with this
		//		data is lost.
		if ( redrawExtras || extrasOverlay.contentsLost() ){
			log.debug("Redrawing Overlay");
			
			final Graphics2D og2d = extrasOverlay.createGraphics();	
			setRenderingHints(og2d);

			og2d.setBackground(new Color(0, 0, 0, 0));
			og2d.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
			extrasOverlay.markDirty(0, 0, canvas.getWidth(), canvas.getHeight());
			
			for (FigureElement e : relativeExtra) {
				e.paint(og2d, 1);
			}
			Rectangle rect = this.getVisibleRect();
	
			for (FigureElement e : absoluteExtra) {
				e.paint(og2d, 1.0, rect);
			}
			og2d.dispose();
			
			redrawExtras = false;
		}

		//Re-blit to gl canvas every time
		gl.glEnable(GL.GL_BLEND);
		extrasOverlay.drawAll();
		gl.glDisable(GL.GL_BLEND);
	}

	@Override
	public void dispose(GLAutoDrawable drawable) {
		rr.dispose(drawable);
	}

	@Override
	public void init(GLAutoDrawable drawable) {
		rr.init(drawable);

		GL2 gl = drawable.getGL().getGL2();
		gl.glClearDepth(1.0f); // clear z-buffer to the farthest

		gl.glDepthFunc(GL.GL_LEQUAL); // the type of depth test to do

		extrasOverlay = new Overlay(drawable);
		caretOverlay = new Overlay(drawable);
	}

	@Override
	public void reshape(GLAutoDrawable drawable, int x, int y, int w, int h) {
		GL2 gl = drawable.getGL().getGL2();
		GLU glu = new GLU();

		double ratio = (double) w / (double) h;
		fovX = fovY * ratio;

		gl.glMatrixMode(GLMatrixFunc.GL_PROJECTION);
		gl.glLoadIdentity();
		glu.gluPerspective(fovY, ratio, 0.05f, 100f);
		gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
		
		redrawExtras = true;
	}

	@SuppressWarnings("unused")
	private static class Bounds {
		double xMin, xMax, xSize;
		double yMin, yMax, ySize;
		double zMin, zMax, zSize;
		double rMax;
	}

	private Bounds cachedBounds = null;
	/**
	 * Calculates the bounds for the current configuration
	 * 
	 * @return
	 */
	private Bounds calculateBounds() {
		if ( cachedBounds != null ){
			return cachedBounds;
		} else {
			Bounds b = new Bounds();
			Collection<Coordinate> bounds = configuration.getBounds();
			for (Coordinate c : bounds) {
				b.xMax = Math.max(b.xMax, c.x);
				b.xMin = Math.min(b.xMin, c.x);
	
				b.yMax = Math.max(b.yMax, c.y);
				b.yMin = Math.min(b.yMin, c.y);
	
				b.zMax = Math.max(b.zMax, c.z);
				b.zMin = Math.min(b.zMin, c.z);
	
				double r = MathUtil.hypot(c.y, c.z);
				b.rMax = Math.max(b.rMax, r);
			}
			b.xSize = b.xMax - b.xMin;
			b.ySize = b.yMax - b.yMin;
			b.zSize = b.zMax - b.zMin;
			cachedBounds = b;
			return b;
		}
	}

	private void setupView(GL2 gl, GLU glu) {
		gl.glLoadIdentity();

		gl.glLightfv(GLLightingFunc.GL_LIGHT1, GLLightingFunc.GL_POSITION,
				lightPosition, 0);

		// Get the bounds
		Bounds b = calculateBounds();

		// Calculate the distance needed to fit the bounds in both the X and Y
		// direction
		// Add 10% for space around it.
		double dX = (b.xSize * 1.2 / 2.0)
				/ Math.tan(Math.toRadians(fovX / 2.0));
		double dY = (b.rMax * 2.0 * 1.2 / 2.0)
				/ Math.tan(Math.toRadians(fovY / 2.0));

		// Move back the greater of the 2 distances
		glu.gluLookAt(0, 0, Math.max(dX, dY), 0, 0, 0, 0, 1, 0);

		gl.glRotated(yaw * (180.0 / Math.PI), 0, 1, 0);
		gl.glRotated(roll * (180.0 / Math.PI), 1, 0, 0);

		// Center the rocket in the view.
		gl.glTranslated(-b.xMin - b.xSize / 2.0, 0, 0);
		
		//Change to LEFT Handed coordinates
		gl.glScaled(1, 1, -1);
		gl.glFrontFace(GL.GL_CW);
		
		//Flip textures for LEFT handed coords
		gl.glMatrixMode(GL.GL_TEXTURE);
		gl.glLoadIdentity();
		gl.glScaled(-1,1,1);
		gl.glTranslated(-1,0,0);
		gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
	}

	/**
	 * Call when the rocket has changed
	 */
	public void updateFigure() {
		log.debug("3D Figure Updated");
		cachedBounds = null;
		rr.updateFigure();
		internalRepaint();
	}

	private void internalRepaint(){
		super.repaint();
		if (canvas != null)
			canvas.display();
	}
	
	@Override
	public void repaint() {
		redrawExtras = true;
		internalRepaint();
	}

	private Set<RocketComponent> selection = new HashSet<RocketComponent>();

	public void setSelection(RocketComponent[] selection) {
		this.selection.clear();
		if (selection != null) {
			for (RocketComponent c : selection)
				this.selection.add(c);
		}
		internalRepaint();
	}

	private void setRoll(double rot) {
		if (MathUtil.equals(roll, rot))
			return;
		this.roll = MathUtil.reduce360(rot);
		internalRepaint();
	}

	private void setYaw(double rot) {
		if (MathUtil.equals(yaw, rot))
			return;
		this.yaw = MathUtil.reduce360(rot);
		internalRepaint();
	}

	// ///////////// Extra methods

	private Coordinate project(Coordinate c, GL2 gl, GLU glu) {
		double[] mvmatrix = new double[16];
		double[] projmatrix = new double[16];
		int[] viewport = new int[4];

		gl.glGetIntegerv(GL.GL_VIEWPORT, viewport, 0);
		gl.glGetDoublev(GLMatrixFunc.GL_MODELVIEW_MATRIX, mvmatrix, 0);
		gl.glGetDoublev(GLMatrixFunc.GL_PROJECTION_MATRIX, projmatrix, 0);

		double out[] = new double[4];
		glu.gluProject(c.x, c.y, c.z, mvmatrix, 0, projmatrix, 0, viewport, 0,
				out, 0);

		return new Coordinate(out[0], out[1], out[2]);
	}

	private Coordinate cp = new Coordinate(0, 0, 0);
	private Coordinate cg = new Coordinate(0, 0, 0);

	public void setCG(Coordinate cg) {
		this.cg = cg;
		redrawExtras = true;
	}

	public void setCP(Coordinate cp) {
		this.cp = cp;
		redrawExtras = true;
	}

	public void addRelativeExtra(FigureElement p) {
		relativeExtra.add(p);
		redrawExtras = true;
	}

	public void removeRelativeExtra(FigureElement p) {
		relativeExtra.remove(p);
		redrawExtras = true;
	}

	public void clearRelativeExtra() {
		relativeExtra.clear();
		redrawExtras = true;
	}

	public void addAbsoluteExtra(FigureElement p) {
		absoluteExtra.add(p);
		redrawExtras = true;
	}

	public void removeAbsoluteExtra(FigureElement p) {
		absoluteExtra.remove(p);
		redrawExtras = true;
	}

	public void clearAbsoluteExtra() {
		absoluteExtra.clear();
		redrawExtras = true;
	}

	private ComponentSelectionListener csl;

	public static interface ComponentSelectionListener {
		public void componentClicked(RocketComponent[] components, MouseEvent e);
	}

	public void addComponentSelectionListener(
			ComponentSelectionListener newListener) {
		this.csl = newListener;
	}
	
	public void setType(int t){
		if ( t == TYPE_FIGURE ){
			rr.setRenderStrategy(new FigureRenderStrategy());
		} else {
			rr.setRenderStrategy(new RealisticRenderStrategy(document));
		}
		repaint();
	}

}
