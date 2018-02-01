package gate.creole;

import static gate.util.maven.Utils.getRepositoryList;
import static gate.util.maven.Utils.getRepositorySession;
import static gate.util.maven.Utils.getRepositorySystem;

import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JFileChooser;

import org.apache.maven.settings.building.SettingsBuildingException;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.version.Version;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.jdom.xpath.XPath;

import gate.creole.metadata.AutoInstance;
import gate.creole.metadata.CreoleResource;
import gate.gui.ActionsPublisher;
import gate.gui.MainFrame;
import gate.resources.img.svg.ApplicationIcon;
import gate.swing.XJFileChooser;
import gate.util.ExtensionFileFilter;

@CreoleResource(tool = true, isPrivate = true, autoinstances = @AutoInstance, name = "Upgrade XGapp", comment = "Upgrades an XGapp to use new style GATE plugins")
public class UpgradeXGAPP extends AbstractResource implements ActionsPublisher {

  private static final long serialVersionUID = -816227103591230313L;

  private static XMLOutputter outputter =
      new XMLOutputter(Format.getPrettyFormat());

  @SuppressWarnings("unchecked")
  public static List<UpgradePath> suggest(Document doc)
      throws IOException, JDOMException {

    List<UpgradePath> upgrades = new ArrayList<UpgradePath>();

    Element root = doc.getRootElement();

    Element pluginList = root.getChild("urlList").getChild("localList");

    List<Element> plugins = pluginList.getChildren();

    Iterator<Element> it = plugins.iterator();
    while(it.hasNext()) {
      Element plugin = it.next();
      switch(plugin.getName()){
        case "gate.util.persistence.PersistenceManager-URLHolder":
          String urlString = plugin.getChild("urlString").getValue();
          String[] parts = urlString.split("/");

          String oldName = parts[parts.length - 1];
          String newName = oldName.toLowerCase().replaceAll("[\\s_]+", "-");

          VersionRangeResult versions =
              getPluginVersions("uk.ac.gate.plugins", newName);

          if(versions != null) {

            upgrades
                .add(new UpgradePath(plugin, urlString, "uk.ac.gate.plugins",
                    newName, versions, versions.getHighestVersion()));

            break;
          }
        case "gate.creole.Plugin-Maven":
          // TODO check to see if there is a newer version of the plugin to use
          break;
        default:
          // some unknown plugin type
          break;
      }
    }

    return upgrades;
  }

  @SuppressWarnings("unchecked")
  public static void upgrade(Document doc, List<UpgradePath> upgrades)
      throws JDOMException {
    Element root = doc.getRootElement();

    Element pluginList = root.getChild("urlList").getChild("localList");

    for(UpgradePath upgrade : upgrades) {
      int pluginIndex = pluginList.indexOf(upgrade.getOldElement());

      pluginList.setContent(pluginIndex, upgrade.getNewElement());
    }

    XPath jarXPath = XPath.newInstance("//urlString");
    for(Element element : (List<Element>)jarXPath.selectNodes(doc)) {

      String urlString = element.getValue();

      for(UpgradePath upgrade : upgrades) {
        if(urlString.startsWith(upgrade.getOldPath())) {

          String urlSuffix = urlString.substring(upgrade.getOldPath().length());

          urlString = upgrade.getNewPath()
              + (urlSuffix.startsWith("resources/") ? "" : "resources/")
              + urlSuffix;

          Element rr = new Element(
              "gate.util.persistence.PersistenceManager-RRPersistence");
          Element uriString = new Element("uriString");
          uriString.setText(urlString);

          rr.addContent(uriString);

          Element parent = element.getParentElement().getParentElement();
          parent.removeContent(element.getParentElement());

          parent.addContent(rr);

          break;
        }
      }
    }

  }

  private static VersionRangeResult getPluginVersions(String group,
      String artifact) {
    try {
      Artifact artifactObj =
          new DefaultArtifact(group, artifact, "jar", "[0,)");

      List<RemoteRepository> repos = getRepositoryList();

      RepositorySystem repoSystem = getRepositorySystem();

      RepositorySystemSession repoSession =
          getRepositorySession(repoSystem, null);

      VersionRangeRequest request =
          new VersionRangeRequest(artifactObj, repos, null);

      VersionRangeResult versionResult =
          repoSystem.resolveVersionRange(repoSession, request);

      if(versionResult.getVersions().isEmpty()) return null;

      List<Version> versions = versionResult.getVersions();

      Iterator<Version> it = versions.iterator();
      while(it.hasNext()) {

        Version version = it.next();
        try {

          artifactObj =
              new DefaultArtifact(group, artifact, "jar", version.toString());

          ArtifactRequest artifactRequest =
              new ArtifactRequest(artifactObj, repos, null);

          ArtifactResult artifactResult =
              repoSystem.resolveArtifact(repoSession, artifactRequest);

          // TODO check if it is compatible with this version of GATE

          URL artifactURL = new URL("jar:"
              + artifactResult.getArtifact().getFile().toURI().toURL() + "!/");

          // check it has a creole.xml at the root
          URL directoryXmlFileUrl = new URL(artifactURL, "creole.xml");

          try (InputStream creoleStream = directoryXmlFileUrl.openStream()) {
            // no op
          }
        } catch(ArtifactResolutionException | IOException e) {
          e.printStackTrace();
          it.remove();
        }
      }

      versionResult.setVersions(versions);

      return versionResult;

    } catch(SettingsBuildingException | VersionRangeResolutionException e) {
      e.printStackTrace();
      return null;
    }
  }

  static class UpgradePath {
    private Element oldEntry;

    private String groupID, artifactID;

    private Version version;

    private VersionRangeResult versions;

    private String oldPath;

    protected UpgradePath(Element oldEntry, String oldPath, String groupID,
        String artifactID, VersionRangeResult versions, Version version) {
      this.oldEntry = oldEntry;
      this.oldPath = oldPath.endsWith("/") ? oldPath : oldPath + "/";
      this.groupID = groupID;
      this.artifactID = artifactID;
      this.versions = versions;
      this.version = version;
    }

    public void setVersion(Version version) {
      if(!versions.getVersions().contains(version))
        throw new IllegalArgumentException("Supplied version isn't valid");

      this.version = version;
    }

    public Version getVersion() {
      return version;
    }

    public String toString() {
      return oldPath + " can be upgraded to one of the following versions of "
          + groupID + ":" + artifactID + " " + versions;
    }

    public String getNewPath() {
      return "creole://" + groupID + ";" + artifactID + ";" + version.toString()
          + "/";
    }

    public String getOldPath() {
      return oldPath;
    }

    protected Element getNewElement() {
      Element mavenPlugin = new Element("gate.creole.Plugin-Maven");

      Element groupElement = new Element("group");
      groupElement.setText(groupID);

      Element artifactElement = new Element("artifact");
      artifactElement.setText(artifactID);

      Element versionElement = new Element("version");
      versionElement.setText(version.toString());

      mavenPlugin.addContent(groupElement);
      mavenPlugin.addContent(artifactElement);
      mavenPlugin.addContent(versionElement);

      return mavenPlugin;
    }

    protected Element getOldElement() {
      return oldEntry;
    }
  }

  private List<Action> actions = null;

  @Override
  public List<Action> getActions() {
    if(actions != null) return actions;

    actions = new ArrayList<Action>();

    actions.add(new UpgradeAction());

    return actions;
  }

  private static class UpgradeAction extends AbstractAction {

    private static final long serialVersionUID = 5104380211427809600L;

    public UpgradeAction() {
      super("Upgrade XGapp", new ApplicationIcon(24, 24));
    }

    @Override
    public void actionPerformed(ActionEvent arg0) {

      XJFileChooser fileChooser = MainFrame.getFileChooser();

      ExtensionFileFilter filter = new ExtensionFileFilter(
          "GATE Application files (.gapp, .xgapp)", ".gapp", ".xgapp");
      fileChooser.addChoosableFileFilter(filter);
      fileChooser.setFileFilter(filter);
      fileChooser.setDialogTitle("Select an XGapp to Upgrade");
      fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
      fileChooser.setResource("lastapplication");

      if(fileChooser.showOpenDialog(
          MainFrame.getInstance()) != XJFileChooser.APPROVE_OPTION)
        return;

      File originalXGapp = fileChooser.getSelectedFile();

      try {
        SAXBuilder builder = new SAXBuilder(false);
        Document doc = builder.build(originalXGapp);
        List<UpgradePath> upgrades = suggest(doc);
        for(UpgradePath upgrade : upgrades) {
          System.out.println(upgrade);
        }

        upgrade(doc, upgrades);

        if(!originalXGapp
            .renameTo(new File(originalXGapp.getAbsolutePath() + ".bak"))) {
          System.err.println("unable to back up existing xgapp");
          return;
        }

        try (FileOutputStream out = new FileOutputStream(originalXGapp)) {
          outputter.output(doc, out);
        }
      } catch(Exception e) {
        e.printStackTrace();
      }
    }

  }

}
