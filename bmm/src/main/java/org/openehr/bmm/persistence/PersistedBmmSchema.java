package org.openehr.bmm.persistence;

/*
 * #%L
 * OpenEHR - Java Model Stack
 * %%
 * Copyright (C) 2016 - 2017  Cognitive Medical Systems, Inc (http://www.cognitivemedicine.com).
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 * Author: Claude Nanjo
 */

import org.apache.commons.lang.StringUtils;
import org.openehr.bmm.core.*;
import org.openehr.bmm.persistence.serializer.BmmSchemaSerializer;
import org.openehr.bmm.persistence.validation.BmmDefinitions;
import org.openehr.bmm.persistence.validation.BmmSchemaValidator;
import org.openehr.utils.common.Counter;

import java.io.Serializable;
import java.util.*;
import java.util.function.Consumer;

/**
 * Created by cnanjo on 4/11/16.
 */
public class PersistedBmmSchema extends PersistedBmmPackageContainer implements IBmmSchemaCore, Serializable {

    /*******************************************************************
     * Instance variables and constructors
     *******************************************************************/

    private final PersistedBmmSchemaState stateCreated = PersistedBmmSchemaState.STATE_CREATED;
    private final PersistedBmmSchemaState stateValidatedCreate = PersistedBmmSchemaState.STATE_VALIDATED_CREATED;
    private final PersistedBmmSchemaState stateIncludesPending = PersistedBmmSchemaState.STATE_INCLUDES_PENDING;
    private final PersistedBmmSchemaState stateIncludesProcessed = PersistedBmmSchemaState.STATE_INCLUDES_PROCESSED;
    private final Integer maxAddAttempts = 10;

    /**
     * Version of BMM model, enabling schema evolution reasoning. Persisted attribute.
     */
    private String bmmVersion;
    /**
     * Inclusion list, in the form of a hash of individual include specifications, each of which at least specifies the id of another schema, and may specify a namespace via which types from the included schemas are known in this schema. Persisted attribute.
     */
    private Map<String, BmmIncludeSpecification> includes;
    /**
     * Primitive type definitions. Persisted attribute.
     */
    private Map<String, PersistedBmmClass> primitives;
    /**
     * Class definitions. Persisted attribute.
     */
    private Map<String, PersistedBmmClass> classDefinitions;
    /**
     * Generated by create_bmm_schema from persisted elements.
     */
    private BmmModel bmmModel;
    /**
     * Current processing state; value from P_BMM_SCHEMA_STATE.
     */
    private PersistedBmmSchemaState state;

    /**
     * package structure in which all top-level qualified package names like xx.yy.zz have been
     * expanded out to a hierarchy of BMM_PACKAGE objects
     */
    private Map<String, PersistedBmmPackage> canonicalPackages;

    private BmmSchemaValidator bmmSchemaValidator;

    /**
     * True if the bmm_version attribute was actually set in the schema; False means
     * the `assumed_bmm_version' value is used instead. This flag enables the situation to
     * be reported properly during schema validation.
     */
    private boolean bmmVersionFromSchema;

    /**
     * Adaptee class for schema metadata.
     */
    private final BmmSchemaCore bmmSchemaCore;

    private List<String> mergedSchema;

    private List<String> includesToProcess;

    public PersistedBmmSchema() {
        this(new BmmSchemaCore());
    }

    public PersistedBmmSchema(BmmSchemaCore schemaCore) {
        primitives = new LinkedHashMap<>();
        classDefinitions = new LinkedHashMap<>();
        includes = new LinkedHashMap<>();
        includesToProcess = new ArrayList<>();
        mergedSchema = new ArrayList<>();
        canonicalPackages = new LinkedHashMap<>();
        bmmSchemaValidator = new BmmSchemaValidator(this);
        this.bmmSchemaCore = schemaCore;
        state = stateCreated;
    }

    public PersistedBmmSchema(String aRmPublisher, String aSchemaName, String aRmRelease) {
        this(new BmmSchemaCore(aRmPublisher, aSchemaName, aRmRelease));
    }

    /*******************************************************************
     * Accessors
     *******************************************************************/

    /**
     * Returns the publisher of model expressed in the schema.
     *
     * @return
     */
    @Override
    public String getRmPublisher() {
        return bmmSchemaCore.getRmPublisher();
    }

    /**
     * Sets the publisher of model expressed in the schema.
     *
     * @param rmPublisher
     */
    @Override
    public void setRmPublisher(String rmPublisher) {
        bmmSchemaCore.setRmPublisher(rmPublisher);
    }

    public boolean isBmmVersionFromSchema() {
        return bmmVersionFromSchema;
    }

    public void setBmmVersionFromSchema(boolean bmmVersionFromSchema) {
        this.bmmVersionFromSchema = bmmVersionFromSchema;
    }

    /**
     * Returns the release of model expressed in the schema as a 3-part numeric, e.g. "3.1.0" .
     *
     * @return
     */
    @Override
    public String getRmRelease() {
        return bmmSchemaCore.getRmRelease();
    }

    /**
     * Sets the release of model expressed in the schema as a 3-part numeric, e.g. "3.1.0" .
     *
     * @param rmRelease
     */
    @Override
    public void setRmRelease(String rmRelease) {
        bmmSchemaCore.setRmRelease(rmRelease);
    }

    /**
     * Returns the name of model expressed in schema; a 'schema' usually contains all of the packages of one 'model'
     * of a publisher. A publisher with more than one model can have multiple schemas. Persisted attribute.
     *
     * @return
     */
    @Override
    public String getSchemaName() {
        return bmmSchemaCore.getSchemaName();
    }

    /**
     * Sets the name of model expressed in schema; a 'schema' usually contains all of the packages of one 'model'
     * of a publisher. A publisher with more than one model can have multiple schemas. Persisted attribute.
     *
     * @param schemaName
     */
    @Override
    public void setSchemaName(String schemaName) {
        bmmSchemaCore.setSchemaName(schemaName);
    }

    /**
     * Returns the revision of schema.
     *
     * @return
     */
    @Override
    public String getSchemaRevision() {
        return bmmSchemaCore.getSchemaRevision();
    }

    /**
     * Sets the revision of schema.
     *
     * @param schemaRevision
     */
    @Override
    public void setSchemaRevision(String schemaRevision) {
        bmmSchemaCore.setSchemaRevision(schemaRevision);
    }

    /**
     * Returns the lifecycle state of the schema.
     *
     * @return
     */
    @Override
    public String getSchemaLifecycleState() {
        return bmmSchemaCore.getSchemaLifecycleState();
    }

    /**
     * Sets the lifecycle state of the schema.
     *
     * @param schemaLifecycleState
     */
    @Override
    public void setSchemaLifecycleState(String schemaLifecycleState) {
        bmmSchemaCore.setSchemaLifecycleState(schemaLifecycleState);
    }

    /**
     * Returns the primary author of schema.
     *
     * @return
     */
    @Override
    public String getSchemaAuthor() {
        return bmmSchemaCore.getSchemaAuthor();
    }

    /**
     * Sets the primary author of schema.
     *
     * @param schemaAuthor
     */
    @Override
    public void setSchemaAuthor(String schemaAuthor) {
        bmmSchemaCore.setSchemaAuthor(schemaAuthor);
    }

    /**
     * Returns the description of schema.
     *
     * @return
     */
    @Override
    public String getSchemaDescription() {
        return bmmSchemaCore.getSchemaDescription();
    }

    /**
     * Sets the description of schema.
     *
     * @param schemaDescription
     */
    @Override
    public void setSchemaDescription(String schemaDescription) {
        bmmSchemaCore.setSchemaDescription(schemaDescription);
    }

    /**
     * Returns the contributing authors of schema.
     *
     * @return
     */
    @Override
    public List<String> getSchemaContributors() {
        return bmmSchemaCore.getSchemaContributors();
    }

    /**
     * Sets the contributing authors of schema.
     *
     * @param schemaContributors
     */
    @Override
    public void setSchemaContributors(List<String> schemaContributors) {
        bmmSchemaCore.setSchemaContributors(schemaContributors);
    }

    /**
     * Returns the name of a parent class used within the schema to provide archetype capability, enabling filtering of
     * classes in RM visualisation. If empty, 'Any' is assumed. Persisted attribute.
     *
     * @return
     */
    @Override
    public String getArchetypeParentClass() {
        return bmmSchemaCore.getArchetypeParentClass();
    }

    /**
     * Sets the name of a parent class used within the schema to provide archetype capability, enabling filtering of
     * classes in RM visualisation. If empty, 'Any' is assumed. Persisted attribute.
     *
     * @param archetypeParentClass
     */
    @Override
    public void setArchetypeParentClass(String archetypeParentClass) {
        bmmSchemaCore.setArchetypeParentClass(archetypeParentClass);
    }

    /**
     * Returns the name of a parent class of logical 'data types' used within the schema to provide archetype capability, enabling
     * filtering of classes in RM visualisation. If empty, 'Any' is assumed. Persisted attribute.
     *
     * @return
     */
    @Override
    public String getArchetypeDataValueParentClass() {
        return bmmSchemaCore.getArchetypeDataValueParentClass();
    }

    /**
     * Sets the name of a parent class of logical 'data types' used within the schema to provide archetype capability,
     * enabling filtering of classes in RM visualisation. If empty, 'Any' is assumed. Persisted attribute.
     *
     * @param archetypeDataValueParentClass
     */
    @Override
    public void setArchetypeDataValueParentClass(String archetypeDataValueParentClass) {
        bmmSchemaCore.setArchetypeDataValueParentClass(archetypeDataValueParentClass);
    }

    /**
     * List of top-level package paths that provide the RM 'model' part in archetype identifiers, e.g. the path "org.openehr.ehr"
     * gives "EHR" in "openEHR-EHR". Within this namespace, archetypes can be based on any class reachable from classes defined directly in these packages.
     *
     * @return
     */
    @Override
    public List<String> getArchetypeRmClosurePackages() {
        return bmmSchemaCore.getArchetypeRmClosurePackages();
    }

    /**
     * Returns the list of top-level package paths that provide the RM 'model' part in achetype identifiers, e.g. the path
     * "org.openehr.ehr" gives "EHR" in "openEHR-EHR". Within this namespace, archetypes can be based on any class reachable
     * from classes defined directly in these packages.
     *
     * @param rmClosurePackages
     */
    @Override
    public void setArchetypeRmClosurePackages(List<String> rmClosurePackages) {
        bmmSchemaCore.setArchetypeRmClosurePackages(rmClosurePackages);
    }

    /**
     * Method adds a top-level package paths that provide the RM 'model' part in achetype identifiers, e.g. the path
     * "org.openehr.ehr" gives "EHR" in "openEHR-EHR". Within this namespace, archetypes can be based on any class reachable from classes defined directly in these packages.
     *
     * @param rmClosurePackage
     */
    @Override
    public void addArchetypeRmClosurePackage(String rmClosurePackage) {
        bmmSchemaCore.addArchetypeRmClosurePackage(rmClosurePackage);
    }

    /**
     * Method returns a class whose descendants should be made visible in tree and grid renderings of the archetype
     * definition, if archetype_parent_class is not set, designate . For openEHR and CEN this class is normally the
     * same as the archetype_parent_class, i.e. LOCATABLE and RECORD_COMPONENT respectively. It is typically set for CEN,
     * because archetype_parent_class may not be stated, due to demographic types not inheriting from it.
     *
     * @return
     */
    @Override
    public String getArchetypeVisualizeDescendantsOf() {
        return bmmSchemaCore.getArchetypeVisualizeDescendantsOf();
    }

    /**
     * Method a class whose descendants should be made visible in tree and grid renderings of the archetype
     * definition, if archetype_parent_class is not set, designate . For openEHR and CEN this class is normally the
     * same as the archetype_parent_class, i.e. LOCATABLE and RECORD_COMPONENT respectively. It is typically set for CEN,
     * because archetype_parent_class may not be stated, due to demographic types not inheriting from it.
     *
     * @param archetypeVisualizeDescendantsOf
     */
    @Override
    public void setArchetypeVisualizeDescendantsOf(String archetypeVisualizeDescendantsOf) {
        bmmSchemaCore.setArchetypeVisualizeDescendantsOf(archetypeVisualizeDescendantsOf);
    }

    /**
     * Returns the derived name of schema, based on model publisher, model name, model release.
     *
     * @return
     */
    @Override
    public String getSchemaId() {
        return bmmSchemaCore.getSchemaId();
    }

    /**
     * Method returns the version of BMM model, enabling schema evolution reasoning. Persisted attribute.
     *
     * @return
     */
    public String getBmmVersion() {
        return bmmVersion;
    }

    /**
     * Method sets the version of BMM model, enabling schema evolution reasoning. Persisted attribute.
     *
     * @param bmmVersion
     */
    public void setBmmVersion(String bmmVersion) {
        this.bmmVersion = bmmVersion;
    }

    /**
     * Method returns BmmModel generated from this schema definition.
     *
     * @return
     */
    public BmmModel getBmmModel() {
        return bmmModel;
    }

    /**
     * Method sets the BmmModel derived from this schema.
     *
     * @param bmmModel
     */
    protected void setBmmModel(BmmModel bmmModel) {
        this.bmmModel = bmmModel;
    }

    /**
     * Method returns the current processing state.
     *
     * @return
     */
    public PersistedBmmSchemaState getState() {
        return state;
    }

    /**
     * Method sets the current processing state.
     *
     * @param state
     */
    public void setState(PersistedBmmSchemaState state) {
        this.state = state;
    }

    /*******************************************************************
     * Operations on schema primitives
     *******************************************************************/

    /**
     * Method returns primitive type definitions as a shallow clone.
     *
     * @return
     */
    public List<PersistedBmmClass> getPrimitives() {
        List<PersistedBmmClass> retVal = new ArrayList<>();
        retVal.addAll(primitives.values());
        return retVal;
    }

    /**
     * Method sets primitive type definitions.
     *
     * @param primitives
     */
    public void setPrimitives(List<PersistedBmmClass> primitives) {
        for (PersistedBmmClass primitive : primitives) {
            this.primitives.put(primitive.getName().toUpperCase(), primitive);
        }
    }

    /**
     * Method adds primitive type to the schema.
     *
     * @param primitive
     */
    public void addPrimitive(PersistedBmmClass primitive) {
        this.primitives.put(primitive.getName().toUpperCase(), primitive);
    }

    /**
     * True if `a_class_name' is a primitive type in the model. Note that a_type_name
     * could be a generic type string; only the root class is considered
     *
     * @param aClassName
     * @return
     */
    public boolean hasPrimitiveType(String aClassName) {
        return primitives.containsKey(aClassName.toUpperCase());
    }

    /**
     * Returns the primitive that matches the given primitive name or null if no such primitive exists in the schema
     *
     * @param name
     * @return
     */
    public PersistedBmmClass getPrimitive(String name) {
        return this.primitives.get(name.toUpperCase());
    }

    /*******************************************************************
     * Operations on schema classes
     *******************************************************************/

    /**
     * Method returns non-primitive class definitions.
     *
     * @return
     */
    public List<PersistedBmmClass> getClassDefinitions() {
        List<PersistedBmmClass> retVal = new ArrayList<>();
        retVal.addAll(this.classDefinitions.values());
        return retVal;
    }

    /**
     * Method sets non-primitive class definitions.
     *
     * @param classDefinitions
     */
    public void setClassDefinitions(List<PersistedBmmClass> classDefinitions) {
        classDefinitions.forEach(classDef -> {
            this.classDefinitions.put(classDef.getName().toUpperCase(), classDef);
        });
    }

    /**
     * True if `a_class_name' has a class definition in the schema.
     *
     * @param aClassName
     * @return
     */
    public boolean hasClassDefinitionO(String aClassName) {
        return classDefinitions.containsKey(aClassName.toUpperCase());
    }

    /**
     * Method adds class definition to schema.
     *
     * @param classDefinition
     */
    public void addClassDefinition(PersistedBmmClass classDefinition) {
        this.classDefinitions.put(classDefinition.getName().toUpperCase(), classDefinition);
    }

    /**
     * Returns class definition corresponding to `classname'
     * Note: does not search for primitives of that name.
     *
     * @param className
     * @return
     */
    public PersistedBmmClass getClassDefinition(String className) {
        return this.classDefinitions.get(className.toUpperCase());
    }

    /**
     * Finds class either among class definitions or primitive definitions in case primitives are used directly as types.
     *
     * @param className
     * @return
     */
    public PersistedBmmClass findClassOrPrimitiveDefinition(String className) {
        PersistedBmmClass bmmClass = getClassDefinition(className);
        if (bmmClass == null) {
            bmmClass = getPrimitive(className.toUpperCase());
        }
        return bmmClass;
    }

    /**
     * True if `a_class_name' has a class definition or is a primitive type in the model. Note that a_type_name
     * could be a generic type string; only the root class is considered
     *
     * @param aClassName
     * @return
     */
    public boolean hasClassOrPrimitiveDefinition(String aClassName) {
        return hasClassDefinitionO(aClassName.toUpperCase()) || hasPrimitiveType(aClassName.toUpperCase());
    }

    /*******************************************************************
     * Helpful lambdas for classes
     *******************************************************************/

    /**
     * Do some action to all primitive type and class objects
     * process in breadth first order of inheritance tree
     *
     * @param action
     * @param classesToProcess
     */
    public void doAllClassesInOrder(Consumer<PersistedBmmClass> action, List<PersistedBmmClass> classesToProcess) {
        int attempts = getClassDefinitions().size() * 10;
        int tries = 0;
        List<String> visitedClasses = new ArrayList<>();
        Queue<PersistedBmmClass> queue = new LinkedList<>();
        //Initial queue population
        for (PersistedBmmClass bmmClass : classesToProcess) {
            processClass(action, visitedClasses, queue, bmmClass);
        }
        //Go through the queue and remove nodes whose ancestors have already been processed
        while (!queue.isEmpty() && tries < attempts) {
            PersistedBmmClass element = queue.remove();
            if (element == null) {
                System.out.println("Pause here");
            } else {
                processClass(action, visitedClasses, queue, element);
            }
            tries++;
        }
    }

    /**
     * Do some action to all primitive type and class objects
     * process in any order
     *
     * @param action
     */
    public void doAllClasses(Consumer<PersistedBmmClass> action) {
        getPrimitives().forEach(action);
        getClassDefinitions().forEach(action);
    }

    /*******************************************************************
     * Operations on schema includes
     *******************************************************************/

    /**
     * Method returns the inclusion list, in the form of a hash of individual include specifications, each of which at
     * least specifies the id of another schema, and may specify a namespace via which types from the included schemas
     * are known in this schema. Persisted attribute.
     *
     * @return
     */
    public Map<String, BmmIncludeSpecification> getIncludes() {
        return includes;
    }

    /**
     * list of includes to process during setup. A clone of all include references is returned.
     *
     * @return
     */
    public List<String> getIncludesToProcess() {
        return includesToProcess;
    }

    /**
     * Method sets the inclusion map, in the form of a hash of individual include specifications, each of which at
     * least specifies the id of another schema, and may specify a namespace via which types from the included schemas
     * are known in this schema. Persisted attribute.
     *
     * @param includes
     */
    public void setIncludes(Map<String, BmmIncludeSpecification> includes) {
        this.includes = includes;
    }

    /**
     * Method sets the inclusion list, in the form of a hash of individual include specifications, each of which at
     * least specifies the id of another schema, and may specify a namespace via which types from the included schemas
     * are known in this schema. Persisted attribute.
     *
     * @param includes
     */
    public void setIncludes(List<BmmIncludeSpecification> includes) {
        includes.forEach(include -> {
            this.includes.put(include.getId().toUpperCase(), include);
        });
    }

    /**
     * Method adds include to schema.
     *
     * @param include
     */
    public void addInclude(BmmIncludeSpecification include) {
        includes.put(include.getId().toUpperCase(), include);
    }

    /**
     * Creates a BMM Include Spec from BMM Schema argument.
     * ID = RM_Publisher + "_" + Schema_Name + "_" + RM_Release lowercased.
     * Namespace = Schema_Name lowercased.
     */
    public void addInclude() {
        String id = generateSchemaIdentifier();
        String namespace = getSchemaName().toLowerCase();
        BmmIncludeSpecification spec = new BmmIncludeSpecification(id, namespace);
        includes.put(id.toUpperCase(), spec);
    }

    /*******************************************************************
     * Operations on schema packages
     *******************************************************************/

    /**
     * Returns the top-level packages for this schema.
     */
    public Map<String, PersistedBmmPackage> getCanonicalPackages() {
        return canonicalPackages;
    }

    /**
     * Sets the top-level package list for this schema.
     *
     * @param canonicalPackages
     */
    public void setCanonicalPackages(Map<String, PersistedBmmPackage> canonicalPackages) {
        this.canonicalPackages = canonicalPackages;
    }

    /**
     * Adds a top-level package to this list of top-level packages.
     *
     * @param aPackage
     */
    public void addCanonicalPackage(PersistedBmmPackage aPackage) {
        this.canonicalPackages.put(aPackage.getName().toUpperCase(), aPackage);
    }

    /**
     * Returns the top-level package by that name or null if none is found.
     *
     * @param aPackageName
     * @return
     */
    public PersistedBmmPackage getCanonicalPackage(String aPackageName) {
        return this.canonicalPackages.get(aPackageName.toUpperCase());
    }

    /**
     * True if there is a package at the path `a_path' under this package
     * <p>
     * TODO Make sure regex is correct and validate with test.
     *
     * @param aPath
     * @return
     */
    public boolean hasCanonicalPackagePath(String aPath) {
        boolean retVal = false;
        if(StringUtils.isEmpty(aPath)) {
            retVal = false;
        } else {
            String[] packageNames = aPath.toUpperCase().split("\\" + org.openehr.bmm.persistence.validation.BmmDefinitions.PACKAGE_NAME_DELIMITER);
            ;
            PersistedBmmPackageContainer packageContainer = null;
            PersistedBmmPackage currentPackage = canonicalPackages.get(packageNames[0]);
            if (packageNames.length == 1 && currentPackage != null) {
                //Path consists of a single package and it is a top-level package.
                retVal = true;
            } else if (currentPackage == null) {
                //No top level package found
                retVal = false;
            } else {
                for (int index = 1; index < packageNames.length; index++) {
                    if (currentPackage.getPackages().containsKey(packageNames[index])) {
                        currentPackage = currentPackage.getPackages().get(packageNames[index]);
                        retVal = true;
                    } else {
                        retVal = false;
                        break;
                    }
                }
            }
        }
        return retVal;
    }

    /*******************************************************************
     * Schema serialization methods
     *******************************************************************/

    public String serialize() {
        BmmSchemaSerializer serializer = new BmmSchemaSerializer(this);
        return serializer.serialize();
    }

    /*******************************************************************
     * Schema loading and schema component factory methods
     *******************************************************************/

    /**
     * ID = RM_Publisher + "_" + Schema_Name + "_" + RM_Release lowercased with spaces replaced with dashes.
     *
     * @return
     */
    public String generateSchemaIdentifier() {
        return (getRmPublisher() + "_" + getSchemaName() + "_" + getRmRelease()).toLowerCase().replace(" ", "-");
    }

    /**
     * Finalisation work:
     * 1. convert packages to canonical form, i.e. full hierarchy with no packages with names like aa.bb.cc
     * 2. set up include processing list
     */
    public void loadFinalize() {
        PersistedBmmPackage childPackage = null;
        String childPackageKey = null;
        Map<String, PersistedBmmPackage> packageContainer = null;
        if(state == PersistedBmmSchemaState.STATE_VALIDATED_CREATED) {
            //top-level package canonicalisation: the result is that in each P_BMM_SCHEMA, the
            //attribute `canonical_packages' contains the mergeable structure
            for (PersistedBmmPackage topPackage : getPackages().values()) {
                //Iterate over qualified name, inserting new packages for each of these names.
                //E.g. 'rm.composition.content' causes three new packages 'rm', 'composition'
                // and 'content' to be created and linked, with the 'rm' one being put in
                // `canonical_packages'
                if (topPackage.getName().indexOf(org.openehr.bmm.persistence.validation.BmmDefinitions.PACKAGE_NAME_DELIMITER) >= 0) {
                    packageContainer = canonicalPackages;
                    String[] packagePathComponents = topPackage.getName().split("\\.");
                    for (int index = 0; index < packagePathComponents.length; index++) {
                        childPackageKey = packagePathComponents[index].toUpperCase();
                        if (packageContainer.containsKey(childPackageKey)) {
                            childPackage = packageContainer.get(childPackageKey);
                        } else {
                            childPackage = new PersistedBmmPackage(packagePathComponents[index]);
                            packageContainer.put(childPackageKey, childPackage);
                        }
                        packageContainer = childPackage.getPackages();
                    }
                    //make this package with `packages' and `classes' references to those parts of `other_pkg'
                    //but keeping its own name.
                    childPackage.makeFromOther(topPackage);
                } else {
                    //just create a reference in the canonical packages; note that this precludes
                    //the situation where top-level packages like 'rm' and 'rm.composition.content'
                    //co-exist - this would be bad structure
                    canonicalPackages.put(topPackage.getName().toUpperCase(), topPackage);
                }
            }
            //set up the includes processing list
            if (includes.isEmpty()) {
                state = PersistedBmmSchemaState.STATE_INCLUDES_PROCESSED;
            } else {
                for (String include : includes.keySet()) {
                    BmmIncludeSpecification includeSpecification = includes.get(include);
                    includesToProcess.add(includeSpecification.getId());
                }
                state = PersistedBmmSchemaState.STATE_INCLUDES_PENDING;
            }
        } else {
            throw new RuntimeException("Invalid state " + state + ". Expected STATE_VALIDATED_CREATED");
        }
    }

    /**
     * Generate a BMM_SCHEMA object
     */
    public void createBmmSchema() {
        if (state == PersistedBmmSchemaState.STATE_INCLUDES_PROCESSED) {
            BmmModel model = new BmmModel();
            /******* Pass 1 *******/
            model.setRmPublisher(getRmPublisher());
            model.setSchemaName(getSchemaName());
            model.setRmRelease(getRmRelease());
            model.setSchemaAuthor(getSchemaAuthor());
            model.setSchemaContributors(getSchemaContributors());
            model.setSchemaLifecycleState(getSchemaLifecycleState());
            model.setSchemaRevision(getSchemaRevision());
            model.setSchemaDescription(getSchemaDescription());
            //Add packages first
            getPackages().values().forEach(p -> {
                p.createBmmPackageDefinition(null);
                BmmPackage packageDef = p.getBmmPackageDefinition();
                if (packageDef != null) {
                    model.addPackage(p.getBmmPackageDefinition());
                }
            });
            //then add classes starting from the highest ancestor down to leaf-level classes
            getPackages().values().forEach(persistedBmmPackage -> {
                persistedBmmPackage.doRecursiveClasses((p, s) -> {
                    PersistedBmmClass persistedBmmClass = findClassOrPrimitiveDefinition(s);
                    if (persistedBmmClass != null) {
                        persistedBmmClass.createBmmClass();
                        BmmClass bmmClass = persistedBmmClass.getBmmClass();
                        BmmPackage bmmPackage = p.getBmmPackageDefinition();
                        if (bmmClass != null && bmmPackage != null) {
                            if (this.primitives.get(bmmClass.getName().toUpperCase()) != null) {
                                bmmClass.setPrimitiveType(true);
                            }
                            if (persistedBmmClass.isOverride()) {
                                bmmClass.setOverride(true);
                            }
                            model.addClassDefinition(bmmClass, bmmPackage);
                        }
                    }
                });
            });
            model.setArchetypeParentClass(getArchetypeParentClass());
            model.setArchetypeDataValueParentClass(getArchetypeDataValueParentClass());
            model.setArchetypeVisualizeDescendantsOf(getArchetypeVisualizeDescendantsOf());
            //model.setArchetypeRmClosurePackages(getArchetypeRmClosurePackages().clone());//Support deep clone. Investigating.
            /******* Pass 2 *******/
            doAllClassesInOrder(bmmClass -> {
                bmmClass.populateBmmClass(model);
            }, getPrimitives());
            doAllClassesInOrder(bmmClass -> {
                bmmClass.populateBmmClass(model);
            }, getClassDefinitions());

            bmmModel = model;
        } else {
            throw new RuntimeException("Schema can't be processed. It is in an invalid state " + state);
        }
    }

    /**
     * Process a class by handling its ancestors first.
     *
     * @param action
     * @param visitedClasses
     * @param queue
     * @param bmmClass
     */
    protected void processClass(Consumer<PersistedBmmClass> action, List<String> visitedClasses, Queue<PersistedBmmClass> queue, PersistedBmmClass bmmClass) {
        if (!visitedClasses.contains(bmmClass.getName().toUpperCase())) {
            boolean allAncestorsAndDependenciesVisited = true;
            for (String ancestor : bmmClass.getAncestors()) {
                if (!visitedClasses.contains(ancestor.toUpperCase())) {
                    allAncestorsAndDependenciesVisited = false;
                    PersistedBmmClass ancestorDef = findClassOrPrimitiveDefinition(ancestor);
                    queue.add(ancestorDef);
                }

            }
            if (bmmClass.isGeneric()) {
                Map<String, PersistedBmmGenericParameter> parameters = bmmClass.getGenericParameterDefinitions();
                for (PersistedBmmGenericParameter parameter : parameters.values()) {
                    String conformsTo = parameter.getConformsToType();
                    if (!visitedClasses.contains(conformsTo.toUpperCase())) {
                        allAncestorsAndDependenciesVisited = false;
                        PersistedBmmClass dependency = findClassOrPrimitiveDefinition(conformsTo);
                        queue.add(dependency);
                    }
                }
            }
            if (!allAncestorsAndDependenciesVisited) {
                queue.add(bmmClass);
            } else {
                action.accept(bmmClass);
                visitedClasses.add(bmmClass.getName().toUpperCase());
            }
        }
    }

    public void merge(PersistedBmmSchema toBeMerged) {
        //Handle packages
        toBeMerged.getPackages().forEach((packageName, bmmPackage) -> {
            PersistedBmmPackage sourcePackage = getPackage(packageName);
            if (sourcePackage == null) { ////If a package is new at that level, add it and its children.
                addPackage(bmmPackage.deepClone());
            } else {
                sourcePackage.merge(bmmPackage);
            }
        });
        //If a package already exist, merge its classes, for each child package repeat...
        //Merge class definitions first. If you see a class with the same name, log it (complain) - OpenEHR has no notion of namespaces. Need to fix spec to support them.
        toBeMerged.getClassDefinitions().forEach(classDef -> {
            addClassDefinition(classDef.deepClone());
        });
        //Handle primitives. If you see a class with the same name, log it (complain) - OpenEHR has no notion of namespaces. Need to fix spec to support them.
        toBeMerged.getPrimitives().forEach(classDef -> {
            addPrimitive(classDef.deepClone());
        });
        mergedSchema.add(toBeMerged.generateSchemaIdentifier().toUpperCase());
    }

    public void populateAllAncestorsForClass(PersistedBmmClass bmmClass, List<String> allAncestorList) {
        List<String> ancestorList = bmmClass.getAncestors();
        ancestorList.forEach(ancestorName -> {
            PersistedBmmClass ancestorClass = classDefinitions.get(ancestorName);
            if (ancestorClass == null) {
                //TODO add an error
            } else {
                allAncestorList.add(ancestorName);
                List<String> ancestorAncestors = ancestorClass.getAncestors();
                if (ancestorAncestors != null) {
                    ancestorAncestors.forEach(ancestorsAncestorName -> {
                        allAncestorList.add(ancestorsAncestorName);
                        PersistedBmmClass ancestorsAncestorClass = classDefinitions.get(ancestorsAncestorName);
                        populateAllAncestorsForClass(ancestorsAncestorClass, allAncestorList);
                    });
                }
            }
        });
    }

    public void finalizeLoad() {

        //Set bmm_version if not found in schema
        if (bmmVersion != null) {
            bmmVersionFromSchema = true;
        } else {
            bmmVersion = BmmDefinitions.ASSUMED_BMM_VERSION;
        }

        //has to be done in two gos, because changing keys mess with the table structure if done in one pass
        Map<String, PersistedBmmClass> updatedPrimitives = new HashMap<>();
        primitives.forEach((key, value) -> {
            updatedPrimitives.put(key.toUpperCase(), value);
        });
        primitives = updatedPrimitives;

        Map<String, PersistedBmmClass> updatedClasses = new HashMap<>();
        classDefinitions.forEach((key, value) -> {
            updatedClasses.put(key.toUpperCase(), value);
        });
        classDefinitions = updatedClasses;

        //set all packages keys to upper case
        correctPackageKeys();

        //assign unique ids to all class objects, to enable collision detection during merging
        doAllClasses(persistedBmmClass -> {
            persistedBmmClass.setUid(Counter.instance.getAndIncrement());
            persistedBmmClass.setSourceSchemaId(getSchemaId());
        });
    }

    /*******************************************************************
     * Validation framework
     *******************************************************************/

    /**
     * Returns the BMMSchemaValidator for this schema
     *
     * @return
     */
    public BmmSchemaValidator getBmmSchemaValidator() {
        return bmmSchemaValidator;
    }

    /**
     * Sets the BmmSchemaValidator for this schema
     *
     * @param bmmSchemaValidator
     */
    public void setBmmSchemaValidator(BmmSchemaValidator bmmSchemaValidator) {
        this.bmmSchemaValidator = bmmSchemaValidator;
    }

    /**
     * Indicates whether the persistent schema has been fully loaded including
     * all includes and is now ready to be validated.
     *
     * @return
     */
    public boolean readyToValidate() {
        return this.state == PersistedBmmSchemaState.STATE_INCLUDES_PROCESSED;
    }

    /**
     * Method validates the given schema
     *
     * @return
     */
    public void validate() {
        bmmSchemaValidator.validate();
    }

    /*******************************************************************
     * Other convenience methods
     *******************************************************************/

    public String toString() {
        return generateSchemaIdentifier();
    }
}
