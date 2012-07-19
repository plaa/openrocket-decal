package net.sf.openrocket.startup;
import java.awt.BorderLayout;
import java.io.File;
import java.lang.reflect.Method;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import net.sf.openrocket.database.ComponentPresetDatabase;
import net.sf.openrocket.database.ThrustCurveMotorSetDatabase;
import net.sf.openrocket.document.OpenRocketDocument;
import net.sf.openrocket.file.DatabaseMotorFinder;
import net.sf.openrocket.file.GeneralRocketLoader;
import net.sf.openrocket.gui.main.componenttree.ComponentTree;
import net.sf.openrocket.gui.scalefigure.RocketPanel;
import net.sf.openrocket.gui.util.GUIUtil;
import net.sf.openrocket.gui.util.SwingPreferences;
import net.sf.openrocket.l10n.ResourceBundleTranslator;

/**
 * An application for quickly testing 3d figure witout all the OpenRocket user
 * interface
 * 
 * @author bkuker
 * 
 */
public class TextureTest {

	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
		Application.setBaseTranslator(new ResourceBundleTranslator("l10n.messages"));
		Application.setMotorSetDatabase(new ThrustCurveMotorSetDatabase(false) {
			{
				startLoading();
			}

			@Override
			protected void loadMotors() {
			}
		});
		Application.setPreferences(new SwingPreferences());

		// Must be done after localization is initialized
		ComponentPresetDatabase componentPresetDao = new ComponentPresetDatabase(true) {

			@Override
			protected void load() {
			}
			
		};
		Application.setComponentPresetDao(componentPresetDao);
		
		OpenRocketDocument doc = new GeneralRocketLoader().load(
				new File("3d-Test-Junk/net/sf/openrocket/startup/al1 Apocalypse_54mmtestFr.rkt.xml"),
				new DatabaseMotorFinder());

		GUIUtil.setBestLAF();
		
		JFrame ff = new JFrame();
		ff.setSize(1200, 400);
		ff.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		doc.getDefaultConfiguration().setAllStages();

		final RocketPanel panel = new RocketPanel(doc);

		ComponentTree ct = new ComponentTree(doc);
		panel.setSelectionModel(ct.getSelectionModel());

		JPanel p = new JPanel();
		p.setLayout(new BorderLayout());
		p.add(ct, BorderLayout.WEST);
		p.add(panel, BorderLayout.CENTER);
		ff.setContentPane(p);
		ff.setVisible(true);

		SwingUtilities.invokeLater(new Runnable() {

			@Override
			public void run() {
				try {
					Method m = panel.getClass().getDeclaredMethod("go3D");
					m.setAccessible(true);
					m.invoke(panel);
				} catch (Throwable t) {
					t.printStackTrace();
				}
			}
		});

	}
}
