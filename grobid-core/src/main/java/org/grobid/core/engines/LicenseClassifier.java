package org.grobid.core.engines;

import java.util.*;
import org.apache.commons.lang3.StringUtils;

import org.grobid.core.data.CopyrightsLicense;
import org.grobid.core.data.CopyrightsLicense.CopyrightsOwner;
import org.grobid.core.data.CopyrightsLicense.License;
import org.grobid.core.exceptions.GrobidException;
import org.grobid.core.utilities.TextUtilities;
import org.grobid.core.utilities.UnicodeUtil;
import org.grobid.core.utilities.GrobidProperties;
import org.grobid.core.GrobidModels;
import org.grobid.core.factory.GrobidFactory;
import org.grobid.core.jni.PythonEnvironmentConfig;
import org.grobid.core.jni.DeLFTClassifierModel;
import org.grobid.core.utilities.GrobidConfig.ModelParameters;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.core.io.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LicenseClassifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(LicenseClassifier.class);

    // multi-class/multi-label classifier
    private DeLFTClassifierModel classifierCopyrightsOwner = null;
    private DeLFTClassifierModel classifierLicense = null;

    // binary classifiers to be added if used

    private Boolean useBinary = false; 

    private JsonParser parser;

    private static volatile LicenseClassifier instance;

    public static LicenseClassifier getInstance() {
        if (instance == null) {
            getNewInstance();
        }
        return instance;
    }

    /**
     * Create a new instance.
     */
    private static synchronized void getNewInstance() {
        instance = new LicenseClassifier();
    }

    private LicenseClassifier() {
        //ModelParameters parameterCopyrightsOwner = GrobidProperties.getModel("copyrights-owner");
        //ModelParameters parameterLicenses = GrobidProperties.getModel("licenses");

        //this.useBinary = configuration.getUseBinaryContextClassifiers();
        if (this.useBinary == null)
            this.useBinary = false;

        this.classifierCopyrightsOwner = new DeLFTClassifierModel("copyright", GrobidProperties.getDelftArchitecture("copyright"));
        this.classifierLicense = new DeLFTClassifierModel("license", GrobidProperties.getDelftArchitecture("license"));
    }

    /**
     * Classify a simple piece of text
     * @return list of predicted labels/scores pairs
     */
    public CopyrightsLicense classify(String text) throws Exception {
        if (StringUtils.isEmpty(text))
            return null;
        List<String> texts = new ArrayList<String>();
        texts.add(text);
        return classify(texts).get(0);
    }

    /**
     * Classify an array of texts
     * @return list of predicted labels/scores pairs for each text
     */
    public List<CopyrightsLicense> classify(List<String> texts) throws Exception {
        if (texts == null || texts.size() == 0)
            return null;

        LOGGER.info("classify: " + texts.size());

        String the_json_copyrights_owner = this.classifierCopyrightsOwner.classify(texts);
        String the_json_licenses = this.classifierLicense.classify(texts);

        List<CopyrightsLicense> results = new ArrayList<>();

        // set resulting context classes to entity mentions
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root_copyrights = mapper.readTree(the_json_copyrights_owner);
            JsonNode root_licenses = mapper.readTree(the_json_licenses);

            int entityRank =0;
            JsonNode classificationsNodeCopyrights = root_copyrights.findPath("classifications");
            JsonNode classificationsNodeLicenses = root_licenses.findPath("classifications");
            if ((classificationsNodeCopyrights != null) && (!classificationsNodeCopyrights.isMissingNode()) && 
                (classificationsNodeLicenses != null) && (!classificationsNodeLicenses.isMissingNode())) {
                Iterator<JsonNode> ite1 = classificationsNodeCopyrights.elements();
                Iterator<JsonNode> ite2 = classificationsNodeLicenses.elements();
                while (ite1.hasNext()) {
                    CopyrightsLicense result = new CopyrightsLicense();
                    JsonNode classificationsNode = ite1.next();

                    List<String> owners = CopyrightsLicense.copyrightOwners;
                    List<Double> scoreFields = new ArrayList<>();

                    for(String fieldOwners : owners) {
                        JsonNode fieldNode = classificationsNode.findPath(fieldOwners);
                        double scoreField = 0.0;
                        if ((fieldNode != null) && (!fieldNode.isMissingNode())) {
                            scoreFields.add(fieldNode.doubleValue());
                        }
                    }

                    CopyrightsOwner owner = null;
                    double bestProb = 0.0;
                    double scoreUndecided = 0.0;
                    int rank = 0;
                    for (Double scoreField : scoreFields) {
                        if (scoreField>0.5 && scoreField >= bestProb) {
                            owner = CopyrightsOwner.valueOf(owners.get(rank).toUpperCase());
                            bestProb = scoreField;
                        }
                        scoreUndecided = scoreField;
                        rank++;
                    }

                    if (owner == null) {
                        owner = CopyrightsOwner.UNDECIDED;
                        bestProb = scoreUndecided;
                    }

                    /*JsonNode publisherNode = classificationsNodeCopyrights.findPath("publisher");
                    JsonNode authorsNode = classificationsNodeCopyrights.findPath("authors");
                    JsonNode undecideNode = classificationsNodeCopyrights.findPath("undecided");
                    JsonNode textNode = classificationsNodeCopyrights.findPath("text");

                    double scorePublisher = 0.0;
                    if ((publisherNode != null) && (!publisherNode.isMissingNode())) {
                        scorePublisher = publisherNode.doubleValue();
                    }

                    double scoreAuthors = 0.0;
                    if ((authorsNode != null) && (!authorsNode.isMissingNode())) {
                        scoreAuthors = authorsNode.doubleValue();
                    }

                    double scoreUndecided = 0.0;
                    if ((undecideNode != null) && (!undecideNode.isMissingNode())) {
                        scoreUndecided = undecideNode.doubleValue();
                    }

                    String textValue = null;
                    if ((textNode != null) && (!textNode.isMissingNode())) {
                        textValue = textNode.textValue();
                    }

                    CopyrightsOwner owner = null;
                    double bestProb = 0.0;

                    if (scorePublisher>0.5) {
                        owner = CopyrightsOwner.PUBLISHER;
                        bestProb = scorePublisher;
                    }
                    if (scoreAuthors > 0.5 && scoreAuthors >= scorePublisher) {
                        owner = CopyrightsOwner.AUTHORS;
                        bestProb = scoreAuthors;
                    }

                    if (scoreUndecided > bestProb) {
                        owner = CopyrightsOwner.UNDECIDED;
                        bestProb = scoreUndecided;
                    }*/

                    // ser best copyright owner with prob
                    result.setCopyrightsOwner(owner);
                    result.setCopyrightsOwnerProb(bestProb);

                    classificationsNode = ite2.next();

                    bestProb = 0.0;
                    List<String> licenses = CopyrightsLicense.licenses;
                    scoreFields = new ArrayList<>();

                    for(String fieldLicenses : licenses) {
                        JsonNode fieldNode = classificationsNode.findPath(fieldLicenses);
                        double scoreField = 0.0;
                        if ((fieldNode != null) && (!fieldNode.isMissingNode())) {
                            scoreFields.add(fieldNode.doubleValue());
                        }
                    }

                    bestProb = 0.0;
                    scoreUndecided = 0.0;
                    License license = null;
                    rank = 0;
                    for (Double scoreField : scoreFields) {
                        if (scoreField>0.5 && scoreField >= bestProb) {
                            String valueLicense = licenses.get(rank);
                            valueLicense = valueLicense.replace("-", "");
                            license = License.valueOf(valueLicense.toUpperCase());
                            bestProb = scoreField;
                        }
                        scoreUndecided = scoreField;
                        rank++;
                    }

                    if (license == null) {
                        license = License.UNDECIDED;
                        bestProb = scoreUndecided;
                    }

                    // get best license with prob
                    result.setLicense(license);
                    result.setLicenseProb(bestProb);

                    results.add(result);
                    entityRank++;
                }
            }
        } catch(JsonProcessingException e) {
            LOGGER.error("failed to parse JSON copyrights/licenses classification result", e);
        }
        
        return results;
    }

}
