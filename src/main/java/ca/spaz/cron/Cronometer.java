package ca.spaz.cron;

import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.List;

import javax.swing.*;

import ca.spaz.cron.actions.CopyServingsToUserAction;
import ca.spaz.cron.actions.CreateRecipeAction;
import ca.spaz.cron.datasource.Datasources;
import ca.spaz.cron.datasource.XMLFoodLoader;
import ca.spaz.cron.foods.Food;
import ca.spaz.cron.foods.FoodEditor;
import ca.spaz.cron.targets.DRITargetModel;
import ca.spaz.cron.targets.TargetEditor;
import ca.spaz.cron.ui.*;
import ca.spaz.cron.user.*;
import ca.spaz.gui.*;
import ca.spaz.util.*;

import com.apple.mrj.MRJAboutHandler;
import com.apple.mrj.MRJQuitHandler;

/**
 * The main app.
 *
 * @author davidson
 */

public class Cronometer extends JFrame implements MRJQuitHandler, MRJAboutHandler, ClipboardOwner {

    public static final String TITLE = "Cronometer";
    public static final int BUILD = 19;
    public static JFrame mainFrame = null;

    private static Clipboard clipboard = new Clipboard ("Cronometer");

    private static DailySummary ds;

    private SpazMenuBar menu;
    private HelpBrowser help;
    private JPanel mainPanel;

    private static Cronometer instance;

    public static Cronometer getInstance() {
        if (null == instance) {
            instance = new Cronometer();
        }
        return instance;
    }

    /**
     * Constructor
     */
    public Cronometer() {
    }

    public static Clipboard getClipboard() {
        return clipboard;
    }

    public static Image getWindowIcon() {
        return ImageFactory.getInstance().loadImage("/img/icon.png");
    }

    private void initGUI() {
        try {
            setJMenuBar(getMenu());
            setIconImage(getWindowIcon());
            setTitle(getFullTitleWithUser());
            if (!UserManager.getSubdirectory().equalsIgnoreCase("cronometer")) {
                setTitle(getFullTitleWithUser() + " [" + UserManager.getSubdirectory() + "]");
            }
            getContentPane().add(getMainPanel());
            setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

            pack();
            Point p = ToolBox.centerFrame(this);
            if (!UserManager.getUserManager().firstCronRun()) {
                UserManager.getUserManager().restoreWindow(Cronometer.getInstance(), p);
                getDailySummary().getDietPanel().setDividerLocation(UserManager.getUserManager().getDietDivider(300));
            }
            setVisible(true);
            mainFrame = this;
            if (UserManager.getUserManager().firstCronRun()) {
                doShowReadMe();
                UserManager.getUserManager().setFirstCronRun(false);
                UserManager.getCurrentUser().doFirstRun(getMainPanel());
                TargetEditor.setDefaultTargets(new DRITargetModel(), UserManager.getCurrentUser());
            } else {
                if (UserManager.getUserManager().getLastBuild() < 19) {
                    doShowCronometerDotCom();
                } else if (UserManager.getUserManager().getLastBuild() < BUILD) {
                    doShowReleaseNotes();
                }
                if (UserManager.getUserManager().getLastBuild() < 7) {
                    upgradeToB7();
                }
            }
            UserManager.getUserManager().setLastBuild(Cronometer.BUILD);
            makeAutoSaveTimer();
            addWindowListener(new WindowAdapter() {
                public void windowClosing(WindowEvent e) {
                    doQuit();
                }
                public void windowIconified(WindowEvent e) {
                    if (UserManager.getUserManager().getHideWhenMinimized()) {
                        setVisible(false);
                    }
                }
            });
        } catch (Exception e) {
            Logger.debug(e);
            ErrorReporter.showError(e, this);
        }
    }

    private void upgradeToB7() {
        if (UserManager.getCurrentUser().isFemale()) {
            JOptionPane.showMessageDialog(getMainPanel(),
                                          "Previous versions of Cronometer were incorrectly \n" +
                                          "suggesting male nutritional targets for women.\n" +
                                          "It is highly recommended that you reset your nutritional\n" +
                                          "targets to values appropriate for women.");
            UserSettingsDialog.showDialog(UserManager.getUserManager(), getMainPanel());
            TargetEditor.setDefaultTargets(new DRITargetModel(), UserManager.getCurrentUser());
            doEditUserSettings();
        }
    }

    public void doAddServing() {
        getDailySummary().getServingTable().doAddServing();
    }

    private void makeAutoSaveTimer() {
        Timer t = new Timer(6000 * 5, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                UserManager.getCurrentUser().saveUserData();
                getDailySummary().refreshTime();
            }
        });
        t.setCoalesce(true);
        t.setRepeats(true);
        t.start();
    }

    public void doShowReleaseNotes() {
        getHelpBrowser().showWindow();
        getHelpBrowser().showPage("release1.0.html");
    }

    public void doShowCronometerDotCom() {
        getHelpBrowser().showWindow();
        getHelpBrowser().showPage("webversion.html");
    }

    public void doExportToWebVersion() {
        ExportWizard.doExport();
    }

    public void doShowReadMe() {
        new ReadMe(this, "Licence Agreement", getClass().getResource("/docs/readme.html")) {
            public boolean isCancellable() {
                return true;
            }
            public void doCancel() {
                System.exit(1);
            }
        };
    }

    public void doHelp() {
        getHelpBrowser().showWindow();
        getHelpBrowser().showPage("introduction.html");
    }

    public HelpBrowser getHelpBrowser() {
        if (help == null) {
            try {
                URL.setURLStreamHandlerFactory(new JarLoader());
                help = new HelpBrowser("Cronometer Help", new URL("class://DocAnchor/"));
                help.setIconImage(Cronometer.getWindowIcon());
                ToolBox.centerFrame(help);
            } catch (Exception e) {
                e.printStackTrace();
                ErrorReporter.showError(e, this);
            }
        }
        return help;
    }

    public void doReportBug() {
        ToolBox.launchURL(Cronometer.getInstance(),
                          "https://github.com/myint/cronometer/issues");
    }

    public void doRequestFeature() {
        doReportBug();
    }

    public void doMinimize() {
        setState(Frame.ICONIFIED);
    }

    public static String getFullTitle() {
        return TITLE;
    }

    public static String getFullTitleWithUser() {
        String heading = getFullTitle();
        User currentUser = UserManager.getCurrentUser();
        if (currentUser == null) {
            return heading;
        } else {
            return heading + " - " + currentUser.getUsername();
        }
    }

    private JMenuBar getMenu() {
        if (null == menu) {
            menu = new SpazMenuBar(getClass().getResourceAsStream("/menubar.xml"), this);

            // insert SWING edit menu:
            JMenu mainMenu = new JMenu("Edit");
            mainMenu.setMnemonic(KeyEvent.VK_E);
            TransferActionListener actionListener = new TransferActionListener();
            int mask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
            JMenuItem menuItem = new JMenuItem("Cut");
            menuItem.setActionCommand((String)TransferHandler.getCutAction().
                                      getValue(Action.NAME));
            menuItem.addActionListener(actionListener);
            menuItem.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_X, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
            menuItem.setMnemonic(KeyEvent.VK_T);
            mainMenu.add(menuItem);
            menuItem = new JMenuItem("Copy");
            menuItem.setActionCommand((String)TransferHandler.getCopyAction().
                                      getValue(Action.NAME));
            menuItem.addActionListener(actionListener);
            menuItem.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_C, mask));
            menuItem.setMnemonic(KeyEvent.VK_C);
            mainMenu.add(menuItem);
            menuItem = new JMenuItem("Copy To...");
            menuItem.setMnemonic('o');
            menuItem.setToolTipText("Copy selected servings to another user");
            menuItem.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    CopyServingsToUserAction.copyToUserDialog();
                }
            });
            mainMenu.add(menuItem);
            menuItem = new JMenuItem("Paste");
            menuItem.setActionCommand((String)TransferHandler.getPasteAction().getValue(Action.NAME));
            menuItem.addActionListener(actionListener);
            menuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_V, mask));
            menuItem.setMnemonic(KeyEvent.VK_P);
            mainMenu.add(menuItem);

            menu.add(mainMenu, 1);

        }
        return menu;
    }

    private JPanel getMainPanel() {
        if (null == mainPanel) {
            mainPanel = new JPanel(new BorderLayout(4, 4));
            mainPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

            mainPanel.add(getDailySummary(), BorderLayout.CENTER);
        }
        return mainPanel;
    }

    public static DailySummary getDailySummary() {
        if (null == ds) {
            ds = new DailySummary();
        }
        return ds;
    }

    public void doCreateNewFood() {
        FoodEditor.editFood(new Food());
        refreshDisplays();
    }

    public void doBrowseFoodDatabase() {
        getDailySummary().getServingTable().doAddServing();
        refreshDisplays();
    }

    public void doCreateNewRecipe() {
        List servings = getDailySummary().getServingTable().getSelectedServings();
        CreateRecipeAction.execute(servings);
        refreshDisplays();
    }

    public void doEditUserSettings() {
        TargetEditor.editTargets();
    }

    public void doManageUsers() {
        UserManager.startUserManagerDialog();
    }

    public void doImportFood() {
        JFileChooser fd = new JFileChooser();
        fd.setMultiSelectionEnabled(true);
        if (fd.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File [] foods = fd.getSelectedFiles();
            if (foods != null) {
                for(int i = 0; i < foods.length; ++i) {
                    File f = foods[i];
                    Food food = XMLFoodLoader.loadFood(f);
                    if (food != null) {
                        Datasources.getUserFoods().addFood(food);
                    }
                }

                JOptionPane.showMessageDialog(this,
                                              foods.length + " food(s) have been added to your foods.",
                                              "Food(s) Added", JOptionPane.INFORMATION_MESSAGE);
                refreshDisplays();
            }
        }
    }

    public void doAbout() {
        getHelpBrowser().showWindow();
        getHelpBrowser().showPage("about.html");
    }

    public void doQuit() {
        try {
            // remember window size & position
            getDailySummary().getNotesEditor().saveCurrentNote();
            UserManager.getUserManager().saveWindow(this);
            UserManager.getUserManager().setDietDivider(getDailySummary().getDietPanel().getDividerLocation());
            Datasources.closeAll();
            UserManager.getUserManager().saveUserProperties();
        } catch (IOException e1) {
            e1.printStackTrace();
            ErrorReporter.showError(e1, this);
        }
        System.exit(0);
    }

    public void doToday() {
        getDailySummary().goToToday();
    }

    public void doPreviousDay() {
        getDailySummary().goToPreviousDay();
    }

    public void doNextDay() {
        getDailySummary().goToNextDay();
    }

    public void handleQuit() {
        doQuit();
    }

    public void doNutritionReport() {
        getDailySummary().getNutritionSummaryPanel().generateReport(getDailySummary().getDate());
    }

    /**
     * Installs special apple event listeners for Mac OS X specific functions
     */
    private void setUpOSX() {
        if (ToolBox.isMacOSX()) {
            try {
                Class appleAppClass = Class.forName("com.apple.mrj.MRJApplicationUtils");

                Class quitClass = Class.forName("com.apple.mrj.MRJQuitHandler");
                Class[] quitArgs = {quitClass};
                Method registerQuitMethod = appleAppClass.getDeclaredMethod("registerQuitHandler", quitArgs);
                if (registerQuitMethod != null) {
                    Object[] args = {this};
                    registerQuitMethod.invoke(appleAppClass, args);
                }

                Class aboutClass = Class.forName("com.apple.mrj.MRJAboutHandler");
                Class[] aboutArgs = {aboutClass};
                Method registerAboutMethod = appleAppClass.getDeclaredMethod("registerAboutHandler", aboutArgs);
                if (registerAboutMethod != null) {
                    Object[] args = {this};
                    registerAboutMethod.invoke(appleAppClass, args);
                }
            } catch (Exception e) {
                Logger.debug(e);
                ErrorReporter.showError(e, this);
            }
        }
    }

    public void handleAbout() {
        doAbout();
    }

    public void lostOwnership(Clipboard clipboard, Transferable contents) {
        // unused interface
        // grey out paste menu?
    }

    /**
     * Some unspecified change has occurred in the underlying data, so refresh the current views.
     * For instance a food may have been edited and the search results and servings table need to
     * reflect the changes made.
     */
    public void refreshDisplays() {
        repaint();
        // TODO: replace this with a direct user servings listener model
        Cronometer.getInstance().getDailySummary().notifyObservers();
    }

    /**
     * Get the toolbar buttons to look and act consistent on both Windows and Mac
     * @param btn the button to modify
     */
    public static void fixButton(final JButton btn) {
        btn.setOpaque(false);
        btn.setRolloverEnabled(true);
        if (ToolBox.isMacOSX()) {
            btn.setBorderPainted(false);
            btn.setContentAreaFilled(false);
            btn.addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) {
                    if (btn.isEnabled()) {
                        btn.setContentAreaFilled(true);
                        btn.setOpaque(false);
                        btn.setBorderPainted(true);
                    }
                }
                public void mouseExited(MouseEvent e) {
                    btn.setContentAreaFilled(false);
                    btn.setBorderPainted(false);
                }
            });
        }
    }

    /**
     * Show window normally
     */
    public void restoreWindow() {
        setVisible(true);
        setExtendedState(Frame.NORMAL);
        toFront();
    }

    /**
     * Show the user a dialog with an Ok button.
     * @param message the message to display
     * @param title the title of the window
     */
    public static void okDialog(String message, String title) {
        JOptionPane.showMessageDialog(getInstance(), message, title, JOptionPane.OK_OPTION);
    }

    public static void main(String[] args) {
        try {
            if (ToolBox.isMacOSX()) {
                System.setProperty("apple.laf.useScreenMenuBar", "true");
            } else {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            }
        } catch (Exception e) {
            Logger.error("Error: setLookAndFeel() failed", e);
        }
        // The optional program argument sets the name of the subdirectory for user data.
        // This permits using a different subdirectory for development and testing
        // (by setting the argument in your IDE's run configuration) than for
        // daily use of the application.
        if (args.length > 0) {
            UserManager.setSubdirectory(args[0]);
        }

        final Cronometer cron = Cronometer.getInstance();
        Datasources.initialize(null);
        cron.initGUI();

        // Do not enable quit until application has completed loading.
        // Otherwise we risk crashes if the user tries to quit during loading.
        cron.setUpOSX();
    }
}
