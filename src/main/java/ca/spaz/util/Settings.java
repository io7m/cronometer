package ca.spaz.util;

import java.io.*;
import java.util.*;

import javax.xml.parsers.*;

import org.w3c.dom.*;
import org.xml.sax.SAXException;

import ca.spaz.cron.user.User;
import ca.spaz.cron.user.UserManager;

/**
 * Handles the storage and retrieval of program Settings.
 * A little more friendly to use than Properties and more
 * flexible than Java Preferences for general use.
 *
 * XML File format used as persistent store.
 * HashTable of key/values is underlying metaphor for use.
 *
 * @author Aaron Davidson <davidson@cs.ualberta.ca>
 */

public class Settings implements Serializable {
    private static File file;
    private static boolean dirty = false;
    private static Settings generalSettings;

    private String settingTag;
    private Hashtable<String, String>  map = new Hashtable<String, String>();
    private Vector<SettingsChangeListener> listeners;

    public static final String TAG_GENERAL = "General";
    public static final String TAG_USER = "User";

    /**
     * There are two flavours of settings, general and user.  By default we create General settings.
     */
    public Settings() {
        this.settingTag = TAG_GENERAL;
        generalSettings = this;
    }

    /**
     * Create a new set of Settings of type defined by calling function.
     */
    public Settings(String settingType) {
        settingTag = settingType;
        if (settingType.equals(TAG_GENERAL)) {
            generalSettings = this;
        }
    }

    /**
     * @param f the File where the settings will be stored
     */
    public void setFile(File f) {
        file = f;
    }

    public boolean isEmpty() {
        return (map.size() == 0);
    }

    public void clearAll() {
        map.clear();
    }

    public String[] keys() {
        Set s = map.keySet();
        return (String[])s.toArray(new String[s.size()]);
    }

    /**
     * given a key Setting name, obtain its value.
     * @param name the name of the Setting.
     * @param def the default to return if value is not present
     * @return the value of the Setting
     */
    public synchronized String getSetting(String name, String def) {
        String s = map.get(name);
        if (s == null) {
            return def;
        }
        return s;
    }

    /**
     * given a key Setting name, obtain its value.
     * @param name the name of the Setting.
     * @return the value of the Setting or null if not found
     */
    public synchronized String getSetting(String name) {
        return map.get(name);
    }

    /**
     * given a key Setting name, obtain its value as an int.
     * @param name the name of the Setting.
     * @return the value of the Setting
     */
    public synchronized int getInt(String name, int def) {
        String s = getSetting(name);
        if (s != null) {
            return Integer.parseInt(s);
        } else {
            return def;
        }
    }

    /**
     * given a key Setting name, obtain its value as an int.
     * @param name the name of the Setting.
     * @return the value of the Setting
     */
    public synchronized long getLong(String name, long def) {
        try {
            String s = getSetting(name);
            if (s != null) {
                return Long.parseLong(s);
            } else {
                return def;
            }
        } catch (Exception e) {
            return def;
        }
    }

    /**
     * given a key Setting name, obtain its value as an int.
     * @param name the name of the Setting.
     * @param def the default value to return if it is not present
     * @return the value of the Setting
     */
    public synchronized double getDouble(String name, double def) {
        String str = this.getSetting(name);
        if (str == null) {
            return def;
        }
        return Double.parseDouble(str);
    }

    /**
     * given a key Setting name, obtain its value as a boolean value.
     * @param name the name of the Setting.
     * @return the value of the Setting
     */
    public synchronized boolean getBoolean(String name, boolean bool) {
        String str = this.getSetting(name);
        if (str == null) {
            return bool;
        }
        return str.equals("true");
    }

    /**
     * Get a Setting.
     * @param name name of the Setting to get
     * @param def the default value if the setting is not found
     * @return the setting value as a string
     */
    public synchronized String get(String name, String def) {
        return getSetting(name, def);
    }

    /**
     * Set a Setting.
     * @param name name of the Setting to set
     * @param val value of the Setting to set
     */
    public synchronized void set(String name, String val) {
        map.put(name, val);
        dirty = true;
        fireSettingChangeEvent(name, val);
    }

    /**
     * Set a Setting.
     * @param name name of the Setting to set
     * @param val value of the Setting to set
     */
    public synchronized void set(String name, int val) {
        set(name, Integer.toString(val));
        dirty = true;
    }

    /**
     * Set a Setting.
     * @param name name of the Setting to set
     * @param val value of the Setting to set
     */
    public synchronized void set(String name, long val) {
        set(name, Long.toString(val));
        dirty = true;
    }

    /**
     * Set a Setting.
     * @param name name of the Setting to set
     * @param val value of the Setting to set
     */
    public synchronized void set(String name, boolean val) {
        set(name, Boolean.toString(val));
        dirty = true;
    }

    /**
     * Set a Setting.
     * @param name name of the Setting to set
     * @param val value of the Setting to set
     */
    public synchronized void set(String name, double val) {
        set(name, Double.toString(val));
        dirty = true;
    }

    /**
     * Flush current Settings to disk.
     */
    public synchronized void save(String fName) {
        file = new File(fName);
        save();
    }

    /**
     * Flush current Settings to disk.
     */
    public synchronized void save() {
        try {
            PrintStream ps = new PrintStream(
                new BufferedOutputStream(new FileOutputStream(file)));
            writeXML(ps);
            ps.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        dirty = false;
    }

    public synchronized void writeXML(PrintStream out) {
        XMLNode node = new XMLNode("Settings");

        // Save the General settings
        settingsToXML(node, generalSettings, null);

        // Save the user settings
        Iterator<User> ul = UserManager.getUserList().listIterator();
        while (ul.hasNext()) {
            User user = ul.next();
            settingsToXML(node, user.getSettings(), user.getUsername());
        }
        node.setPrintNewLines(true);
        node.write(out);
    }

    /**
     * Add the settings to the given XMLNode.
     * @param node the XMLNode where all the settings will be stored
     * @param settings
     * @param username
     */
    private synchronized void settingsToXML(XMLNode node, Settings settings, String username) {
        Enumeration e = settings.map.keys();
        while (e.hasMoreElements()) {
            String key = (String)e.nextElement();
            XMLNode sNode = new XMLNode(settings.settingTag);
            if (settings.isUserSettings()) {
                sNode.addAttribute("username", username);
            }
            sNode.addAttribute("name", key);
            sNode.addAttribute("value", settings.getSetting(key));
            node.addChild(sNode);
        }
    }

    public String toString() {
        return toString('\n');
    }

    public synchronized String toString(char separator) {
        StringBuffer sb = new StringBuffer();
        Enumeration e = map.keys();
        while (e.hasMoreElements()) {
            String k = (String)e.nextElement();
            sb.append(k);
            sb.append('=');
            sb.append(map.get(k));
            sb.append(separator);
        }
        return sb.toString();
    }

    /**
     * Load the settings from the given file.
     * @param file the file where the settings are stored
     * @return the General settings
     */
    public synchronized ArrayList<User> loadSettings(File f) {
        ArrayList<User> userList = null;
        file = f;
        if ( !file.exists() || file.length() == 0 ) {
            User user = new User(new Settings(Settings.TAG_USER));
            user.setUsername(User.DEFAULT_USERNAME);
            user.setFirstRun(true);
            userList = new ArrayList<User>();
            userList.add(user);
            return userList;
        }
        try {
            InputStream in = new BufferedInputStream(new FileInputStream(file));
            userList = loadSettings(in);
            in.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return userList;
    }

    /**
     * Load Settings fresh from disk
     */
    private synchronized ArrayList<User> loadSettings(InputStream in) {
        ArrayList<User> userList = new ArrayList<User>();

        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document d = db.parse(in);
            Element e = d.getDocumentElement();

            NodeList nl = e.getElementsByTagName(TAG_GENERAL);
            for (int i = 0; i < nl.getLength(); i++) {
                Element m = (Element)nl.item(i);
                generalSettings.set(m.getAttribute("name"), m.getAttribute("value"));
            }

            // Get the user settings
            nl = e.getElementsByTagName(TAG_USER);
            for (int i = 0; i < nl.getLength(); i++) {
                Element m = (Element)nl.item(i);

                String username = m.getAttribute("username");
                User user = UserManager.getUser(userList, username);
                if (user == null) {
                    // Found a new user
                    user = new User(new Settings(Settings.TAG_USER));
                    user.setUsername(username);
                    userList.add(user);
                }

                // Update the user's settings
                user.getSettings().set(m.getAttribute("name"), m.getAttribute("value"));
            }

        } catch (SAXException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        }
        dirty = false;
        return userList;
    }

    public String getFileName() {
        if (file != null) {
            return file.getAbsolutePath();
        } else {
            return null;
        }
    }

    public File getFile() {
        return file;
    }

    public synchronized void remove(String name) {
        map.remove(name);
        dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void setDirty(boolean val) {
        dirty = val;
    }

    /**
     * Convert the old XML Settings to the new style.
     * @param in the file where the old settings are.
     * @param out the file where the new settings are.
     */
    public static void convertSettingsFile(InputStream in, PrintStream out) {
        try {
            XMLNode node = new XMLNode("Settings");

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document d = db.parse(in);
            Element e = d.getDocumentElement();

            NodeList nl = e.getElementsByTagName("setting");
            for (int i = 0; i < nl.getLength(); i++) {
                Element m = (Element)nl.item(i);
                if (m.getAttribute("name").startsWith("cron.user.target.")) {
                    XMLNode sNode = new XMLNode("User");
                    sNode.addAttribute("username", User.DEFAULT_USERNAME);
                    sNode.addAttribute("name", m.getAttribute("name").replace("cron.user.", ""));
                    sNode.addAttribute("value", m.getAttribute("value"));
                    node.addChild(sNode);
                } else if (m.getAttribute("name").startsWith("cron.user.main."))  {
                    XMLNode sNode = new XMLNode("General");
                    sNode.addAttribute("name", m.getAttribute("name").replace("cron.user.main.", ""));
                    sNode.addAttribute("value", m.getAttribute("value"));
                    node.addChild(sNode);
                } else if (m.getAttribute("name").equals("cron.user.diet.divider")
                           || m.getAttribute("name").equals("cron.user.track.Chromium")
                           || m.getAttribute("name").equals("cron.user.pref.cron.user.custom.targets")
                           || m.getAttribute("name").equals("cron.user.carb.perc")
                           || m.getAttribute("name").equals("cron.user.fat.perc"))  {
                    XMLNode sNode = new XMLNode("User");
                    sNode.addAttribute("username", User.DEFAULT_USERNAME);
                    sNode.addAttribute("name", m.getAttribute("name").replace("cron.user.", ""));
                    sNode.addAttribute("value", m.getAttribute("value"));
                    node.addChild(sNode);
                } else if (m.getAttribute("name").equals("cron.user.proten.perc")) {
                    XMLNode sNode = new XMLNode("User");
                    sNode.addAttribute("username", User.DEFAULT_USERNAME);
                    sNode.addAttribute("name", "protein.perc"); // Fix an old spelling mistake
                    sNode.addAttribute("value", m.getAttribute("value"));
                    node.addChild(sNode);
                } else if (m.getAttribute("name").equals("cron.user.first.run")) {
                    XMLNode sNode = new XMLNode("User");
                    sNode.addAttribute("username", User.DEFAULT_USERNAME);
                    sNode.addAttribute("name", m.getAttribute("name").replace("cron.user.", ""));
                    sNode.addAttribute("value", m.getAttribute("value"));
                    node.addChild(sNode);
                } else {
                    System.err.println(" Did not catch case: " + m.getAttribute("name"));
                    XMLNode sNode = new XMLNode("User");
                    sNode.addAttribute("username", User.DEFAULT_USERNAME);
                    sNode.addAttribute("name", m.getAttribute("name").replace("cron.user.", ""));
                    sNode.addAttribute("value", m.getAttribute("value"));
                    node.addChild(sNode);
                }
            }

            // Add the changes to the new version
            XMLNode sNode = new XMLNode("User");
            sNode = new XMLNode("General");
            sNode.addAttribute("name", "first.cron.run");
            sNode.addAttribute("value", "false");
            node.addChild(sNode);

            sNode = new XMLNode("General");
            sNode.addAttribute("name", "last.user");
            sNode.addAttribute("value", "Default User");
            node.addChild(sNode);

            node.setPrintNewLines(true);
            node.write(out);
        } catch (SAXException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        }
    }

    private synchronized Vector<SettingsChangeListener> getListeners() {
        if (listeners == null) {
            listeners = new Vector();
        }
        return listeners;
    }

    public synchronized void addSettingChangeListener(SettingsChangeListener l) {
        getListeners().add(l);
    }

    public synchronized void removeSettingChangeListener(SettingsChangeListener l) {
        getListeners().remove(l);
    }

    public synchronized void fireSettingChangeEvent(String key, String val) {
        if (listeners != null) {
            SettingsChangeEvent event = new SettingsChangeEvent(this, key, val);
            for (int i = 0; i < listeners.size(); i++) {
                listeners.get(i).settingChange(event);
            }
        }
    }

    /**
     * Checks if these settings are storing user details
     * @return true if the class instance is of type <code>TAG_USER</code>.
     */
    public boolean isUserSettings() {
        return settingTag.equals(TAG_USER);
    }
}
