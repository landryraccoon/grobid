package org.grobid.core.utilities;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.grobid.core.GrobidModel;
import org.grobid.core.engines.tagging.GrobidCRFEngine;
import org.grobid.core.exceptions.GrobidPropertyException;
import org.grobid.core.exceptions.GrobidResourceException;
import org.grobid.core.utilities.GrobidConfig.ModelParameters;
import org.grobid.core.main.GrobidHomeFinder;
import org.grobid.core.utilities.Consolidation.GrobidConsolidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

/**
 * This class provide methods to set/load/access grobid config value from a yaml config file loaded 
 * in the class {@link GrobidConfig}. 
 * New yaml parameters and former properties should be equivalent via this class. 
 * 
 * TBD: Each parameter can be overridden by a system property having the same name. 
 */
public class GrobidProperties {
    public static final Logger LOGGER = LoggerFactory.getLogger(GrobidProperties.class);

    static final String FOLDER_NAME_MODELS = "models";
    static final String FILE_NAME_MODEL = "model";
    private static final String GROBID_VERSION_FILE = "/grobid-version.txt";
    static final String UNKNOWN_VERSION_STR = "unknown";

    private static GrobidProperties grobidProperties = null;

    // indicate if GROBID is running in server mode or not
    private static boolean contextExecutionServer = false;

    /**
     * {@link GrobidConfig} object containing all config parameters used by grobid.
     */
    private static GrobidConfig grobidConfig = null;


    private static Map<String, ModelParameters> modelMap = null;

    /**
     * Type of CRF framework used
     */
    //private static GrobidCRFEngine grobidCRFEngine = GrobidCRFEngine.WAPITI;

    /**
     * Default consolidation service, if used
     */
    //private static GrobidConsolidationService consolidationService = GrobidConsolidationService.CROSSREF;

    /**
     * Path to pdf to xml converter.
     */
    private static File pathToPdfToXml = null;

    /**
     * Determines the path of grobid-home for all objects of this class. When
     * grobidHome is set, all created objects will refer to that
     * path. When it is reset, old object refer to the old path whereas objects
     * created after reset will refer to the new path.
     */
    static File grobidHome = null;

    private static String GROBID_VERSION = null;

    /**
     * Path to config file
     */
    static File GROBID_CONFIG_PATH = null;

    //private static String pythonVirtualEnv = "";

    /**
     * Returns an instance of {@link GrobidProperties} object. If no one is set, then
     * it creates one
     */
    public static GrobidProperties getInstance() {
        if (grobidProperties == null) {
            return getNewInstance();
        } else {
            return grobidProperties;
        }
    }

    /**
     * Returns an instance of {@link GrobidProperties} object based on a custom grobid-home directory.
     * If no one is set, then it creates one.
     */
    public static GrobidProperties getInstance(GrobidHomeFinder grobidHomeFinder) {
        grobidHome = grobidHomeFinder.findGrobidHomeOrFail();
        return getInstance();
    }

    /**
     * Reload grobidConfig
     */
    public static void reload() {
        getNewInstance();
    }

    /**
     * Creates a new {@link GrobidProperties} object, initializes and returns it.
     *
     * @return GrobidProperties
     */
    protected static synchronized GrobidProperties getNewInstance() {
        LOGGER.debug("synchronized getNewInstance");
        grobidProperties = new GrobidProperties();
        return grobidProperties;
    }

    /**
     * Load the path to GROBID_HOME from the env-entry set in web.xml.
     */
    private static void assignGrobidHomePath() {
        if (grobidHome == null) {
            synchronized (GrobidProperties.class) {
                if (grobidHome == null) {
                    grobidHome = new GrobidHomeFinder().findGrobidHomeOrFail();
                }
            }
        }
    }

    /**
     * Return the grobid-home path.
     *
     * @return grobid home path
     */
    public static File getGrobidHome() {
        return grobidHome;
    }

    public static File getGrobidHomePath() {
        return grobidHome;
    }

    /**
     * Set the grobid-home path.
     */
    public static void setGrobidHome(final String pGROBID_HOME_PATH) {
        if (StringUtils.isBlank(pGROBID_HOME_PATH))
            throw new GrobidPropertyException("Cannot set property grobidHome to null or empty.");

        grobidHome = new File(pGROBID_HOME_PATH);
        // exception if prop file does not exist
        if (!grobidHome.exists()) {
            throw new GrobidPropertyException("Could not read GROBID_HOME, the directory '" + pGROBID_HOME_PATH + "' does not exist.");
        }

        try {
            grobidHome = grobidHome.getCanonicalFile();
        } catch (IOException e) {
            throw new GrobidPropertyException("Cannot set grobid home path to the given one '" + pGROBID_HOME_PATH
                + "', because it does not exist.");
        }
    }

    /**
     * Load the path to grobid config yaml from the env-entry set in web.xml.
     */
    static void loadGrobidConfigPath() {
        LOGGER.debug("loading grobid config yaml");
        if (GROBID_CONFIG_PATH == null) {
            synchronized (GrobidProperties.class) {
                if (GROBID_CONFIG_PATH == null) {
                    GROBID_CONFIG_PATH = new GrobidHomeFinder().findGrobidConfigOrFail(grobidHome);
                }
            }
        }
    }

    /**
     * Return the path to the GROBID yaml config file
     *
     * @return grobid properties path
     */
    public static File getGrobidConfigPath() {
        return GROBID_CONFIG_PATH;
    }

    /**
     * Set the GROBID config yaml file path.
     */
    public static void setGrobidConfigPath(final String pGrobidConfigPath) {
        if (StringUtils.isBlank(pGrobidConfigPath))
            throw new GrobidPropertyException("Cannot set GROBID config file to null or empty.");

        File grobidConfigPath = new File(pGrobidConfigPath);
        // exception if prop file does not exist
        if (!grobidConfigPath.exists()) {
            throw new GrobidPropertyException("Could not read GROBID config file, the file '" + pGrobidConfigPath + "' does not exist.");
        }

        try {
            GROBID_CONFIG_PATH = grobidConfigPath.getCanonicalFile();
        } catch (IOException e) {
            throw new GrobidPropertyException("Cannot set grobid yaml config file path to the given one '" + pGrobidConfigPath
                + "', because it does not exist.");
        }
    }

    /**
     * Create a new object and search where to find the grobid-home folder.
     * First step is to check if the system property GrobidPropertyKeys.PROP_GROBID_HOME
     * is set, then the path matching to that property is used. Otherwise, the
     * method will search for a folder named grobid-home, 
     * if this is also not set, the method will search for a folder named
     * grobid-home in the current project.
     * Finally from the found grobid-home, native and data resource paths are initialized. 
     */
    public GrobidProperties() {
        assignGrobidHomePath();
        loadGrobidConfigPath();
        setContextExecutionServer(false);

        try {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            grobidConfig = mapper.readValue(GROBID_CONFIG_PATH, GrobidConfig.class);
        } catch (IOException exp) {
            throw new GrobidPropertyException("Cannot open GROBID config yaml file at location '" + GROBID_CONFIG_PATH.getAbsolutePath()
                + "'", exp);
        } catch (Exception exp) {
            throw new GrobidPropertyException("Cannot open GROBID config yaml file " + getGrobidConfigPath().getAbsolutePath(), exp);
        }

        //getProps().putAll(getEnvironmentVariableOverrides(System.getenv()));

        //initializePaths();
        // TBD: tmp to be created
        loadPdf2XMLPath();
        createModelMap();
        //loadSequenceLabelingEngine();
    }

    /**
     * Create a map between model names and associated parameters
     */
    private void createModelMap() {
        for(ModelParameters modelParameter : grobidConfig.grobid.models) {
            if (modelMap == null) 
                modelMap = new TreeMap<>();
            modelMap.put(modelParameter.name, modelParameter);
        }
    }

    /** 
     * Return the distinct values of all the engines that are specified in the the config file 
     */
    public static Set<GrobidCRFEngine> getDistinctModels() {
        Set<GrobidCRFEngine> distinctModels = new HashSet<>();
        for(ModelParameters modelParameter : grobidConfig.grobid.models) {
            if (modelParameter.engine == null) {
                // it should not happen normally
                continue;
            }
            GrobidCRFEngine localEngine = GrobidCRFEngine.get(modelParameter.engine);
            if (!distinctModels.contains(localEngine))
                distinctModels.add(localEngine);
        }
        return distinctModels;
    }

    /** Return the distinct values of all the engines specified in the individual model configuration in the property file **/
    /*public static Set<GrobidCRFEngine> getModelSpecificEngines() {
        return getProps().keySet().stream()
            .filter(k -> ((String) k).startsWith(GrobidPropertyKeys.PROP_GROBID_CRF_ENGINE + '.'))
            .map(k -> GrobidCRFEngine.get(StringUtils.lowerCase(getPropertyValue((String) k))))
            .distinct()
            .collect(Collectors.toSet());
    }*/

    /*protected static void loadSequenceLabelingEngine() {
        grobidCRFEngine = GrobidCRFEngine.get(getPropertyValue(GrobidPropertyKeys.PROP_GROBID_CRF_ENGINE,
            GrobidCRFEngine.WAPITI.name()));
    }*/

    /**
     * Returns the current version of GROBID
     *
     * @return GROBID version
     */
    public static String getVersion() {
        if (GROBID_VERSION == null) {
            synchronized (GrobidProperties.class) {
                if (GROBID_VERSION == null) {
                    String grobidVersion = UNKNOWN_VERSION_STR;
                    try (InputStream is = GrobidProperties.class.getResourceAsStream(GROBID_VERSION_FILE)) {
                        grobidVersion = IOUtils.toString(is, "UTF-8");
                    } catch (IOException e) {
                        LOGGER.error("Cannot read Grobid version from resources", e);
                    }
                    GROBID_VERSION = grobidVersion;
                }
            }
        }
        return GROBID_VERSION;
    }

    /**
     * Initialize the different paths set in the configuration file
     * grobid.properties.
     */
    /*protected static void initializePaths() {
        Enumeration<?> properties = getProps().propertyNames();
        for (String propKey; properties.hasMoreElements(); ) {
            propKey = (String) properties.nextElement();
            String propVal = getPropertyValue(propKey, StringUtils.EMPTY);
            if (propKey.endsWith(".path")) {
                File path = new File(propVal);
                if (!path.isAbsolute()) {
                    try {
                        getProps().put(propKey,
                            new File(getGrobidHome().getAbsoluteFile(), path.getPath()).getCanonicalFile().toString());
                    } catch (IOException e) {
                        throw new GrobidResourceException("Cannot read the path of '" + propKey + "'.");
                    }
                }
            }
        }

        // start: creating all necessary folders
        for (String path2create : GrobidPropertyKeys.PATHES_TO_CREATE) {
            String prop = getProps().getProperty(path2create);
            if (prop != null) {
                File path = new File(prop);
                if (!path.exists()) {
                    LOGGER.debug("creating directory {}", path);
                    if (!path.mkdirs())
                        throw new GrobidResourceException("Cannot create the folder '" + path.getAbsolutePath() + "'.");
                }
            }
        }
        // end: creating all necessary folders
    }*/

    /**
     * Returns the temprorary path of grobid
     *
     * @return a directory for temp files
     */
    public static File getTempPath() {
        if (grobidConfig.grobid.temp == null)
            return new File(System.getProperty("java.io.tmpdir"));
        else 
            return new File(grobidConfig.grobid.temp);
    }

    public static void setNativeLibraryPath(final String nativeLibPath) {
        grobidConfig.grobid.nativelibrary = nativeLibPath;
    }

    /**
     * Returns the path to the native libraries as {@link File} object.
     *
     * @return folder that contains native libraries
     */
    public static File getNativeLibraryPath() {
        return new File(grobidConfig.grobid.nativelibrary);
    }

    /**
     * Returns the installation path of DeLFT if set, null otherwise. It is required for using
     * a Deep Learning sequence labelling engine.
     *
     * @return path to the folder that contains the local install of DeLFT
     */
    public static String getDeLFTPath() {
        return grobidConfig.grobid.delft.install;
    }

    public static String getDeLFTFilePath() {
        String rawPath = grobidConfig.grobid.delft.install;
        File pathFile = new File(rawPath);
        if (!Files.exists(Paths.get(rawPath).toAbsolutePath())) {
            rawPath = "../" + rawPath;
            pathFile = new File(rawPath);
        }
        return pathFile.getAbsolutePath();
    }

    public static String getGluttonHost() {
        return grobidConfig.grobid.consolidation.glutton.host;
    }

    public static Integer getGluttonPort() {
        if (grobidConfig.grobid.consolidation.glutton.port == 0)
            return null;
        else
            return Integer.valueOf(grobidConfig.grobid.consolidation.glutton.port);
    }

    public static boolean useELMo(final String modelName) {
        ModelParameters param = modelMap.get(modelName);
        if (param == null) {
            LOGGER.error("No configuration parameter defnied for model " + modelName);
            return false;
        }
        return param.useELMo;
    }

    public static String getDelftArchitecture(final String modelName) {
        ModelParameters param = modelMap.get(modelName);
        if (param == null) {
            LOGGER.error("No configuration parameter defnied for model " + modelName);
            return null;
        }
        return param.architecture;
    }

    public static String getDelftArchitecture(final GrobidModel model) {
        return getDelftArchitecture(model.getModelName());
    }   
    

    /*public static GrobidCRFEngine getGrobidCRFEngine(final String modelName) {
        ModelParameters param = modelMap.get(modelName);
        if (param == null) {
            LOGGER.error("No configuration parameter defnied for model " + modelName);
            return null;
        }
        return param.engine;
    }*/

    /*public static void setDelftArchitecture(final String modelName, final String theArchitecture) {
        setPropertyValue(GrobidPropertyKeys.PROP_DELFT_ARCHITECTURE, theArchitecture);
    }*/

    /**
     * Returns the host for a proxy connection, given in the grobid config file.
     *
     * @return host for connecting crossref
     */
    public static String getProxyHost() {
        return grobidConfig.grobid.proxy.host;
    }

    /**
     * Sets the host a proxy connection, given in the config file.
     *
     * @param host for connecting crossref
     */
    public static void setProxyHost(final String host) {
        grobidConfig.grobid.proxy.host = host;
        System.setProperty("http.proxyHost", "host");
        System.setProperty("https.proxyHost", "host");
    }

    /**
     * Returns the port for a proxy connection, given in the grobid config file.
     *
     * @return port for connecting crossref
     */
    public static Integer getProxyPort() {
        return grobidConfig.grobid.proxy.port;
    }

    /**
     * Set the "mailto" parameter to be used in the crossref query and in User-Agent
     * header, as recommended by CrossRef REST API documentation.
     *
     * @param mailto email parameter to be used for requesting crossref
     */
    public static void setCrossrefMailto(final String mailto) {
        grobidConfig.grobid.consolidation.crossref.mailto = mailto;
    }

    /**
     * Get the "mailto" parameter to be used in the crossref query and in User-Agent
     * header, as recommended by CrossRef REST API documentation.
     *
     * @return string of the email parameter to be used for requesting crossref
     */
    public static String getCrossrefMailto() {
        return grobidConfig.grobid.consolidation.crossref.mailto;
    }

    /**
     * Set the Crossref Metadata Plus authorization token to be used for Crossref
     * requests for the subscribers of this service.  This token will ensure that said
     * requests get directed to a pool of machines that are reserved for "Plus" SLA users.
     *
     * @param token authorization token to be used for requesting crossref
     */
    public static void setCrossrefToken(final String token) {
        grobidConfig.grobid.consolidation.crossref.token = token;
    }

    /**
     * Get the Crossref Metadata Plus authorization token to be used for Crossref
     * requests for the subscribers of this service.  This token will ensure that said
     * requests get directed to a pool of machines that are reserved for "Plus" SLA users.
     *
     * @return authorization token to be used for requesting crossref
     */
    public static String getCrossrefToken() {
        return grobidConfig.grobid.consolidation.crossref.token;
    }

    /**
     * Sets the port for a proxy connection, given in the grobid config file.
     *
     * @param port for connecting crossref
     */
    public static void setProxyPort(int port) {
        grobidConfig.grobid.proxy.port = port;
        System.setProperty("http.proxyPort", ""+port);
        System.setProperty("https.proxyPort", ""+port);
    }

    public static Integer getPdfToXMLMemoryLimitMb() {
        return grobidConfig.grobid.pdf.pdfalto.memory_limit_mb;
    }

    public static Integer getPdfToXMLTimeoutMs() {
        return grobidConfig.grobid.pdf.pdfalto.timeout_sec * 1000;
    }

    /**
     * Returns the number of threads, given in the grobid config file.
     *
     * @return number of threads
     */
    public static Integer getNBThreads() {
        Integer nbThreadsConfig = Integer.valueOf(grobidConfig.grobid.nb_threads);
        if (nbThreadsConfig.intValue() == 0) {
            return Integer.valueOf(Runtime.getRuntime().availableProcessors());
        }
        return nbThreadsConfig;
    }

    // PDF with more blocks will be skipped
    public static Integer getPdfBlocksMax() {
        return grobidConfig.grobid.pdf.blocks_max;
    }

    // PDF with more tokens will be skipped
    public static Integer getPdfTokensMax() {
        return grobidConfig.grobid.pdf.tokens_max;
    }

    /**
     * Sets the number of threads, given in the grobid-property file.
     *
     * @param nbThreads umber of threads
     */
    public static void setNBThreads(int nbThreads) {
        grobidConfig.grobid.nb_threads = nbThreads;
    }

    public static String getLanguageDetectorFactory() {
        String factoryClassName = grobidConfig.grobid.language_detector_factory;
        if (StringUtils.isBlank(factoryClassName)) {
            throw new GrobidPropertyException("Language detection is enabled but a factory class name is not provided");
        }
        return factoryClassName;
    }

    /**
     * Sets if a language id shall be used, given in the grobid-property file.
     *
     * @param useLanguageId true, if a language id shall be used
     */
    /*public static void setUseLanguageId(final String useLanguageId) {
        setPropertyValue(GrobidPropertyKeys.PROP_USE_LANG_ID, useLanguageId);
    }*/

    public static String getSentenceDetectorFactory() {
        String factoryClassName = grobidConfig.grobid.sentence_detector_factory;
        if (StringUtils.isBlank(factoryClassName)) {
            throw new GrobidPropertyException("Sentence detection is enabled but a factory class name is not provided");
        }
        return factoryClassName;
    }

    /**
     * Returns the path to the home folder of pdf to xml converter.
     */
    public static void loadPdf2XMLPath() {
        LOGGER.debug("loading pdfalto command path");
        String pathName = grobidConfig.grobid.pdf.pdfalto.path;

        pathToPdfToXml = new File(pathName);
        if (!pathToPdfToXml.exists()) {
            throw new GrobidPropertyException(
                "Path to pdfalto doesn't exists. " + 
                "Please set the path to pdfalto in the config file");
        }

        pathToPdfToXml = new File(pathToPdfToXml, Utilities.getOsNameAndArch());

        LOGGER.debug("pdfalto executable home directory set to " + pathToPdfToXml.getAbsolutePath());
    }

    /**
     * Returns the path to the home folder of pdf to xml program.
     *
     * @return path to pdf to xml program
     */
    public static File getPdfToXMLPath() {
        return pathToPdfToXml;
    }

    /*private static String getModelPropertySuffix(final String modelName) {
        return modelName.replaceAll("-", "_");
    }*/

    private static String getGrobidCRFEngineName(final String modelName) {
        ModelParameters param = modelMap.get(modelName);
        if (param == null) {
            LOGGER.error("No configuration parameter defnied for model " + modelName);
            return null;
        }
        return param.engine;
    }

    public static GrobidCRFEngine getGrobidCRFEngine(final String modelName) {
        String engineName = getGrobidCRFEngineName(modelName);
        /*if (grobidCRFEngine.name().equals(engineName)) {
            return grobidCRFEngine;
        }*/
        return GrobidCRFEngine.get(engineName);
    }

    public static GrobidCRFEngine getGrobidCRFEngine(final GrobidModel model) {
        return getGrobidCRFEngine(model.getModelName());
    }

    public static File getModelPath(final GrobidModel model) {
        String extension = getGrobidCRFEngine(model).getExt();
        return new File(getGrobidHome(), FOLDER_NAME_MODELS + File.separator
            + model.getFolderName() + File.separator
            + FILE_NAME_MODEL + "." + extension);
    }

    public static File getModelPath() {
        return new File(getGrobidHome(), FOLDER_NAME_MODELS);
    }

    public static File getTemplatePath(final File resourcesDir, final GrobidModel model) {
        File theFile = new File(resourcesDir, "dataset/" + model.getFolderName()
            + "/crfpp-templates/" + model.getTemplateName());
        if (!theFile.exists()) {
            theFile = new File("resources/dataset/" + model.getFolderName()
                + "/crfpp-templates/" + model.getTemplateName());
        }
        return theFile;
    }

    public static File getEvalCorpusPath(final File resourcesDir, final GrobidModel model) {
        File theFile = new File(resourcesDir, "dataset/" + model.getFolderName() + "/evaluation/");
        if (!theFile.exists()) {
            theFile = new File("resources/dataset/" + model.getFolderName() + "/evaluation/");
        }
        return theFile;
    }

    public static File getCorpusPath(final File resourcesDir, final GrobidModel model) {
        File theFile = new File(resourcesDir, "dataset/" + model.getFolderName() + "/corpus");
        if (!theFile.exists()) {
            theFile = new File("resources/dataset/" + model.getFolderName() + "/corpus");
        }
        return theFile;
    }

    public static String getLexiconPath() {
        return new File(getGrobidHome(), "lexicon").getAbsolutePath();
    }

    public static File getLanguageDetectionResourcePath() {
        return new File(getGrobidHome(), "language-detection");
    }

    /**
     * Returns the maximum parallel connections allowed in the pool.
     *
     * @return the number of connections
     */
    public static int getMaxPoolConnections() {
        return grobidConfig.grobid.max_connections;
    }

    /**
     * Returns maximum time to wait before timeout when the pool is full.
     *
     * @return time to wait in milliseconds.
     */
    public static int getPoolMaxWait() {
        return grobidConfig.grobid.pool_max_wait * 1000;
    }

    /**
     * Returns the consolidation service to be used.
     *
     * @return the consolidation service to be used
     */
    public static GrobidConsolidationService getConsolidationService() {
        return GrobidConsolidationService.get(grobidConfig.grobid.consolidation.service);
    }

    /**
     * Set which consolidation service to use
     */
    public static void setConsolidationService(String service) {
        grobidConfig.grobid.consolidation.service = service;
    }

    /**
     * Returns if the execution context is stand alone or server.
     *
     * @return the context of execution. Return false if the property value is
     * not readable.
     */
    public static boolean isContextExecutionServer() {
        return contextExecutionServer;
    }

    /**
     * Set if the execution context is stand alone or server.
     *
     * @param state true to set the context of execution to server, false else.
     */
    public static void setContextExecutionServer(boolean state) {
        contextExecutionServer = state;
    }

    public static String getPythonVirtualEnv() {
        return grobidConfig.grobid.delft.python_virtualEnv;
    }

    public static void setPythonVirtualEnv(String pythonVirtualEnv) {
        grobidConfig.grobid.delft.python_virtualEnv = pythonVirtualEnv;
    }
}
