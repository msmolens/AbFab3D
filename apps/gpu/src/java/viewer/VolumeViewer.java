/*****************************************************************************
 *                        Shapeways, Inc Copyright (c) 2015
 *                               Java Source
 *
 * This source is licensed under the GNU LGPL v2.1
 * Please read http://www.gnu.org/copyleft/lgpl.html for more information
 *
 * This software comes with the standard NO WARRANTY disclaimer for any
 * purpose. Use it at your own risk. If there's a problem you get to fix it.
 *
 ****************************************************************************/
package viewer;

import abfab3d.grid.Bounds;
import abfab3d.util.Units;

import javax.media.opengl.FPSCounter;
import javax.media.opengl.GLProfile;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelListener;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Iterator;

import static abfab3d.util.Output.printf;
import static java.awt.AWTEvent.WINDOW_EVENT_MASK;

/**
 * Real time viewer for Volumes
 *
 * @author Alan Hudson
 */
public class VolumeViewer extends JFrame implements FileHandler, Runnable {
    // Command line arguments
    private static int fullscreen = -1;
    private static boolean stereo = false;
    private static int desiredSamples = 1;
    private static int numCpus = 0;
    private static int stereoMode = 0;
    private static int[] screenSize = null;   // null means not provided

    /** Should we use full screen mode */
    private boolean useFullscreen;

    /** The current navigator */
    private Navigator nav;

    /** The rendering thread */
    private RenderCanvas render;

    /** The status bar */
    protected StatusBar statusBar;

    /** The content pane for the frame */
    private Container mainPane;

    /** Typical usage message with program options */
    private static final String USAGE_MSG =
            "Usage: Browser [options] [filename]\n" +
                    "  -help                   Prints out this help message\n" +
                    "  -fullscreen n           Runs the browser in fullscreen exclusive mode on screen n.  n is optional\n" +
                    "  -stereo quad|alternate  Enables stereo projection output\n" +
                    "  -antialias n            Use n number of multisamples to antialias scene\n" +
                    "  -numCpus n              Select how many cpu's to use.  Defaults to all\n" +
                    "  -nice                   Do not use all the CPU for rendering\n" +
                    "  -screenSize w h         Specify the screen size to use\n";

    public VolumeViewer() {
        super("ShapeJS Volume Viewer");

        GLProfile.initSingleton();

        // TODO: Bah, wish I knew how to get these window dressing params
        int we = 16;
        int he = 62 + 16;

        int size = 1024;
        int width = size + we;
        int height = size + he;

        if (screenSize != null) {
            width = screenSize[0] + we;
            height = screenSize[1] + he;
        }

        GraphicsEnvironment env =
                GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice dev;

        enableEvents(WINDOW_EVENT_MASK);
        JPopupMenu.setDefaultLightWeightPopupEnabled(false);

        if (fullscreen >= 0) {
            useFullscreen = true;
            GraphicsDevice[] gs = env.getScreenDevices();
            if (gs.length < fullscreen + 1) {
                System.out.println("Invalid fullscreen device.  Using default");
                dev = env.getDefaultScreenDevice();

                Dimension dmn = Toolkit.getDefaultToolkit().getScreenSize();
                width = (int) dmn.getWidth();
                height = (int) dmn.getHeight();
            } else {
                dev = gs[fullscreen];
                DisplayMode dm = dev.getDisplayMode();
                width = dm.getWidth();
                height = dm.getHeight();
            }
        } else {
            dev = env.getDefaultScreenDevice();
        }

        if(useFullscreen && !dev.isFullScreenSupported()) {
            System.out.println("Fullscreen not supported");
            useFullscreen = false;
        }

        if(useFullscreen) {
            DisplayMode currentMode = dev.getDisplayMode();
            DisplayMode prefDisplayMode =
                    new DisplayMode(width,
                            height,
                            currentMode.getBitDepth(),
                            DisplayMode.REFRESH_RATE_UNKNOWN);

            setUndecorated(true);
            dev.setFullScreenWindow(this);

            if (dev.isDisplayChangeSupported()) {
                dev.setDisplayMode(prefDisplayMode);
            } else {
                System.out.println("Fullscreen supported but display mode change is not");
            }
        }

        Runtime system_runtime = Runtime.getRuntime();
        system_runtime.addShutdownHook(new Thread(this));

        nav = new ExamineNavigator();
        render = new RenderCanvas(nav);

        createUI();

        add(render.getComponent());

        setSize(width, height);
        setVisible(true);

        if (!useFullscreen && screenSize == null) {
            setLocation(40, 40);
        }

    }


    //---------------------------------------------------------------
    // Methods defined by Runnable
    //---------------------------------------------------------------

    /**
     * Run method for the shutdown hook. This is to deal with someone using
     * ctrl-C to kill the application. Makes sure that all the resources
     * are cleaned up properly.
     */
    public void run()
    {
        shutdownApp();
    }

    /**
     * Close down the application safely by destroying all the resources
     * currently in use.
     */
    private void shutdownApp() {
        render.terminate();
    }

    //---------------------------------------------------------------
    // Methods defined by Window
    //---------------------------------------------------------------

    @Override
    protected void processWindowEvent(WindowEvent e) {

        if (e.getID() == WindowEvent.WINDOW_CLOSING) {

            // Too many threads running, so, calling shutdown does nothing
            System.exit(0);

        } else if (e.getID() == WindowEvent.WINDOW_CLOSED) {
            System.out.println("Shutting down Xj3D");
        }

        // Pass along all other events
        super.processWindowEvent(e);

    }

    /**
     * Load content.
     *
     * @param url The url to load
     */
    public void loadURL(String url) {
        printf("Loading file: %s\n",url);

        // TODO: Set the current bounds
        Bounds bounds = new Bounds();
        double vs = 0.1 * Units.MM;
        nav.setBounds(bounds,vs);
    }

    /**
     * Create the window contents now.
     */
    protected void createUI() {
        java.util.List<Action> actionList = new ArrayList<Action>();

        statusBar = new StatusBar(render, true, true);

        // Create the menu bar
        JMenuBar menuBar = new JMenuBar();
        JMenuItem menuItem;
        JRadioButtonMenuItem rbItem;

        // File Menu
        JMenu fileMenu = new JMenu("File");
        menuBar.add(fileMenu);

        // Open Item
        Action openAction = new OpenAction(this,this,null);
        actionList.add(openAction);
        fileMenu.add(new JMenuItem(openAction));

        ExitAction exitAction = new ExitAction();
        actionList.add(exitAction);

        // Exit Item
        fileMenu.add(new JMenuItem(exitAction));

        // View Menu
        JMenu viewMenu = new JMenu("View");
        menuBar.add(viewMenu);

        // Render Style SubMenu
        JMenu renderStyle = new JMenu("Render Style");

        ButtonGroup rsGroup = new ButtonGroup();
        /*
        PointsStyleAction psa = new PointsStyleAction(universe, statusBar);
        actionList.add(psa);
        rbItem = new JRadioButtonMenuItem(psa);
        rsGroup.add(rbItem);
        renderStyle.add(rbItem);
        LinesStyleAction lsa = new LinesStyleAction(universe, statusBar);
        actionList.add(lsa);
        rbItem = new JRadioButtonMenuItem(lsa);
        rsGroup.add(rbItem);
        renderStyle.add(rbItem);
        ShadedStyleAction ssa = new ShadedStyleAction(universe, statusBar);
        rbItem = new JRadioButtonMenuItem(ssa);
        actionList.add(ssa);
        rsGroup.add(rbItem);
        renderStyle.add(rbItem);
        rbItem.setSelected(true);
        */
        viewMenu.add(renderStyle);
        /*
        RenderStyle[] linkedStyles = new RenderStyle[1];
        linkedStyles[0] = psa;

        lsa.setLinkedStyles(linkedStyles);

        linkedStyles[0] = lsa;

        psa.setLinkedStyles(linkedStyles);

        linkedStyles = new RenderStyle[2];
        linkedStyles[0] = psa;
        linkedStyles[1] = lsa;

        ssa.setLinkedStyles(linkedStyles);
        */
        // Screen Capture SubMenu
        JMenu captureMenu = new JMenu("Screen Capture");
        /*
        // Single Frame Item
        screenShotAction = new ScreenShotAction(console, universe);
        actionList.add(screenShotAction);

        menuItem = new JMenuItem(screenShotAction);
        captureMenu.add(menuItem);
        viewMenu.add(captureMenu);

        // Movie Start Item
        movieStartAction = new MovieAction(true,console, universe);
        actionList.add(movieStartAction);

        menuItem = new JMenuItem(movieStartAction);
        captureMenu.add(menuItem);
        viewMenu.add(captureMenu);

        // Movie End Item
        movieEndAction = new MovieAction(false,console, universe);
        actionList.add(movieEndAction);

        menuItem = new JMenuItem(movieEndAction);
        captureMenu.add(menuItem);

        capAction = new CaptureViewpointsAction(console, universe, viewpointManager);
        actionList.add(capAction);
        menuItem = new JMenuItem(capAction);
        captureMenu.add(menuItem);
        */
        viewMenu.add(captureMenu);

        /*
        // Scene Info Item
        sceneInfoAction = new SceneInfoAction(console, displayManager);
        actionList.add(sceneInfoAction);
        menuItem = new JMenuItem(sceneInfoAction);
        viewMenu.add(menuItem);

        profilingInfoAction = new ProfilingInfoAction(console, universe);
        actionList.add(profilingInfoAction);
        menuItem = new JMenuItem(profilingInfoAction);
        viewMenu.add(menuItem);

        // Scene Tree Item
        sceneTreeAction = new SceneTreeAction(console, universe, mainPane, BorderLayout.WEST);
        actionList.add(sceneTreeAction);
        menuItem = new JMenuItem(sceneTreeAction);
        viewMenu.add(menuItem);
        */
        // Viewpoint Menu
        JMenu viewpointMenu = new JMenu("Viewpoint");
        menuBar.add(viewpointMenu);
        /*
        viewpointMenu.add(new JMenuItem(viewpointToolbar.getNextViewpointAction()));
        actionList.add(viewpointToolbar.getNextViewpointAction());
        viewpointMenu.add(new JMenuItem(viewpointToolbar.getPreviousViewpointAction()));
        actionList.add(viewpointToolbar.getPreviousViewpointAction());
        viewpointMenu.add(new JMenuItem(viewpointToolbar.getHomeViewpointAction()));
        actionList.add(viewpointToolbar.getHomeViewpointAction());
        */
        JMenu navMenu = new JMenu("Navigation");
        menuBar.add(navMenu);
        /*
        navMenu.add(new JMenuItem(nav_tb.getFlyAction()));
        actionList.add(nav_tb.getFlyAction());
        navMenu.add(new JMenuItem(nav_tb.getWalkAction()));
        actionList.add(nav_tb.getWalkAction());
        navMenu.add(new JMenuItem(nav_tb.getExamineAction()));
        actionList.add(nav_tb.getExamineAction());
        navMenu.add(new JMenuItem(nav_tb.getTiltAction()));
        actionList.add(nav_tb.getTiltAction());
        navMenu.add(new JMenuItem(nav_tb.getPanAction()));
        actionList.add(nav_tb.getPanAction());
        navMenu.add(new JMenuItem(nav_tb.getTrackAction()));
        actionList.add(nav_tb.getTrackAction());
        navMenu.add(new JMenuItem(viewpointToolbar.getLookatAction()));
        actionList.add(viewpointToolbar.getLookatAction());
        navMenu.add(new JMenuItem(viewpointToolbar.getFitWorldAction()));
        actionList.add(viewpointToolbar.getFitWorldAction());
        */
        JMenu optionsMenu = new JMenu("Options");
        /*
        antialiasingAction = new AntialiasingAction(this, statusBar);
        actionList.add(antialiasingAction);
        */
        JMenu antialiasingMenu = new JMenu("Anti-Aliasing");
        ButtonGroup antialiasingGroup = new ButtonGroup();

        int n = 2;

        rbItem = new JRadioButtonMenuItem("Disabled");
        if (desiredSamples <= 1)
            rbItem.setSelected(true);
        rbItem.setActionCommand("Disabled");
        //rbItem.addActionListener(antialiasingAction);
        antialiasingMenu.add(rbItem);
        antialiasingGroup.add(rbItem);
        /*
        int maxSamples = antialiasingAction.getMaximumNumberOfSamples();

        while(n <= maxSamples) {
            rbItem = new JRadioButtonMenuItem(n + " Samples", n == desiredSamples);

            rbItem.addActionListener(antialiasingAction);
            rbItem.setActionCommand(Integer.toString(n));
            antialiasingMenu.add(rbItem);
            antialiasingGroup.add(rbItem);

            n = n * 2;
        }
        */
        optionsMenu.add(antialiasingMenu);

        StepsAction stepsAction = new StepsAction(render,statusBar);
        actionList.add(stepsAction);

        JMenu stepsMenu = new JMenu("Eval Steps");
        ButtonGroup stepsGroup = new ButtonGroup();

        int maxSteps = stepsAction.getMaximumNumberOfSteps();
        n = 256;
        while(n <= maxSteps) {
            rbItem = new JRadioButtonMenuItem(n + " Steps", n == stepsAction.getDefaultNumberOfSteps());

            rbItem.addActionListener(stepsAction);
            rbItem.setActionCommand(Integer.toString(n));
            stepsMenu.add(rbItem);
            stepsGroup.add(rbItem);

            n = n * 2;
        }

        optionsMenu.add(stepsMenu);

        menuBar.add(optionsMenu);
        mainPane = getContentPane();

        if (!useFullscreen) {
            printf("Adding status bar");
            JPanel p2 = new JPanel(new BorderLayout());

            p2.add(statusBar, BorderLayout.SOUTH);
            mainPane.add(p2, BorderLayout.SOUTH);

            setJMenuBar(menuBar);
        } else {
            // Need to register all actions with canvas manually
            JComponent comp = (JComponent) getContentPane();
            KeyStroke ks;
            String actionName;

            Iterator<Action> itr = actionList.iterator();
            Action action;

            while(itr.hasNext()) {
                action = itr.next();

                ks = (KeyStroke) action.getValue(AbstractAction.ACCELERATOR_KEY);
                actionName = (String) action.getValue(AbstractAction.SHORT_DESCRIPTION);

                comp.getInputMap().put(ks, actionName);
                comp.getActionMap().put(actionName, action);
            }
        }


    }

    public static final void main(String[] args) {
        int lastUsed = -1;

        for(int i = 0; i < args.length; i++) {
            if(args[i].startsWith("-")) {
                if(args[i].equals("-fullscreen")) {
                    fullscreen = 0;
                    lastUsed = i;

                    try {
                        String val = args[i+1];
                        fullscreen = Integer.valueOf(val).intValue();
                        lastUsed = i + 1;
                    } catch(Exception e) {}
                } else if(args[i].equals("-screenSize")) {
                    lastUsed = i;

                    screenSize = new int[2];

                    try {
                        String val = args[i+1];
                        screenSize[0] = Integer.valueOf(val).intValue();
                        screenSize[1] = Integer.valueOf(val).intValue();
                        lastUsed = i + 1;
                    } catch(Exception e) {
                        System.out.println("Invalid screen size");

                        screenSize = null;
                    }
                } else if(args[i].equals("-stereo")) {
                    stereo = true;
                    String val = args[++i];
                    lastUsed = i;

                    throw new IllegalArgumentException("Stereo not supported yet");
                    /*
                    if (val.equalsIgnoreCase("quad")) {
                        //stereoMode = GraphicsOutputDevice.QUAD_BUFFER_STEREO;
                    } else if (val.equalsIgnoreCase("alternate")) {
                        //stereoMode = GraphicsOutputDevice.ALTERNATE_FRAME_STEREO;
                    } else {
                        System.out.println("Unknown stereo mode: " + val);
                    }
                    */
                } else if(args[i].equals("-help")) {
                    System.out.println(USAGE_MSG);
                    return;
                } else if (args[i].equals("-antialias")) {
                    String val = args[++i];
                    desiredSamples = Integer.valueOf(val).intValue();
                    lastUsed = i;
                } else if (args[i].equals("-numCpus")) {
                    String val = args[++i];
                    numCpus = Integer.valueOf(val).intValue();
                    lastUsed = i;
                } else if (args[i].startsWith("-")) {
                    System.out.println("Unknown flag: " + args[i]);
                    lastUsed = i;
                }
            }
        }

        VolumeViewer vv = new VolumeViewer();

        // The last argument is the filename parameter
        String filename;

        if((args.length > 0) && (lastUsed + 1 < args.length)) {
            filename = args[args.length - 1];

            vv.loadURL(filename);
        }

    }
}
