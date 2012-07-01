package net.sf.openrocket.gui.figure3d;

import java.util.HashMap;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GL2ES1;
import javax.media.opengl.fixedfunc.GLLightingFunc;

import net.sf.openrocket.rocketcomponent.BodyTube;
import net.sf.openrocket.rocketcomponent.ExternalComponent;
import net.sf.openrocket.rocketcomponent.NoseCone;
import net.sf.openrocket.rocketcomponent.RocketComponent;
import net.sf.openrocket.rocketcomponent.SymmetricComponent;
import net.sf.openrocket.rocketcomponent.Transition;
import net.sf.openrocket.startup.Application;
import net.sf.openrocket.util.Color;

public class FigureRenderStrategy extends RenderStrategy {
	private final float[] color = new float[4];

	@Override
	public boolean isDrawn(RocketComponent c) {
		return true;
	}

	@Override
	public boolean isDrawnTransparent(RocketComponent c) {
		if (c instanceof BodyTube)
			return true;
		if (c instanceof NoseCone)
			return false;
		if (c instanceof SymmetricComponent) {
			if (((SymmetricComponent) c).isFilled())
				return false;
		}
		if (c instanceof Transition) {
			Transition t = (Transition) c;
			return !t.isAftShoulderCapped() && !t.isForeShoulderCapped();
		}
		return false;
	}

	private static final HashMap<Class<?>, Color> defaultColorCache = new HashMap<Class<?>, Color>();

	@Override
	public void preGeometry(GL2 gl, RocketComponent c, float alpha) {
		gl.glLightModeli(GL2ES1.GL_LIGHT_MODEL_TWO_SIDE, 1);
		Color figureColor = c.getColor();
		if (figureColor == null) {
			if (defaultColorCache.containsKey(c.getClass())) {
				figureColor = defaultColorCache.get(c.getClass());
			} else {
				figureColor = Application.getPreferences().getDefaultColor(c.getClass());
				defaultColorCache.put(c.getClass(), figureColor);
			}
		}

		// Set up the front A&D color
		convertColor(figureColor, color);
		color[3] = alpha;
		gl.glMaterialfv(GL.GL_FRONT, GLLightingFunc.GL_DIFFUSE, color, 0);
		gl.glMaterialfv(GL.GL_FRONT, GLLightingFunc.GL_AMBIENT, color, 0);

		// Set up the Specular color & Shine
		convertColor(figureColor, color);
		float d = 0.9f;
		float m = (float) getShine(c) / 128.0f;
		color[0] = Math.max(color[0], d) * m;
		color[1] = Math.max(color[1], d) * m;
		color[2] = Math.max(color[2], d) * m;

		gl.glMaterialfv(GL.GL_FRONT, GLLightingFunc.GL_SPECULAR, color, 0);
		gl.glMateriali(GL.GL_FRONT, GLLightingFunc.GL_SHININESS, getShine(c));
		
		color[0] = color[1] = color[2] = 0;
		gl.glMaterialfv(GL.GL_BACK, GLLightingFunc.GL_SPECULAR, color, 0);

		//Back A&D
		convertColor(figureColor, color);
		color[0] = color[0] * 0.4f;
		color[1] = color[1] * 0.4f;
		color[2] = color[2] * 0.4f;
		color[3] = alpha;
		gl.glMaterialfv(GL.GL_BACK, GLLightingFunc.GL_DIFFUSE, color, 0);
		gl.glMaterialfv(GL.GL_BACK, GLLightingFunc.GL_AMBIENT, color, 0);

	}

	@Override
	public void postGeometry(GL2 gl, RocketComponent c, float alpha) {
		//Nothing to do here

	}
	

	
	private static int getShine(RocketComponent c) {
		if (c instanceof ExternalComponent) {
			switch (((ExternalComponent) c).getFinish()) {
			case ROUGH:
				return 10;
			case UNFINISHED:
				return 30;
			case NORMAL:
				return 40;
			case SMOOTH:
				return 80;
			case POLISHED:
				return 128;
			default:
				return 100;
			}
		}
		return 20;
	}

}