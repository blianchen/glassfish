/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2008-2010 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 *
 * Contributor(s):
 *
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package org.glassfish.persistence.jpa;

import com.sun.appserv.connectors.internal.api.ConnectorRuntime;
import com.sun.enterprise.deployment.*;
import org.glassfish.api.deployment.DeploymentContext;
import org.glassfish.api.deployment.MetaData;
import org.glassfish.api.deployment.InstrumentableClassLoader;
import org.glassfish.api.deployment.OpsParams;
import org.glassfish.api.deployment.archive.ReadableArchive;
import org.glassfish.deployment.common.SimpleDeployer;
import org.glassfish.deployment.common.DeploymentException;
import org.glassfish.persistence.common.Java2DBProcessorHelper;
import org.glassfish.persistence.common.DatabaseConstants;
import org.jvnet.hk2.annotations.Inject;
import org.jvnet.hk2.annotations.Service;

import javax.naming.NamingException;
import javax.persistence.spi.ClassTransformer;
import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import javax.validation.ValidatorFactory;
import javax.validation.Validation;
import java.util.*;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;


/**
 * Deployer for JPA applications
 * @author Mitesh Meswani
 */
@Service
public class JPADeployer extends SimpleDeployer<JPAContainer, JPApplicationContainer> {

    @Inject
    private ConnectorRuntime connectorRuntime;

    @Override public MetaData getMetaData() {

        return new MetaData(true /*invalidateCL */ ,
                null /* provides */,
                new Class[] {Application.class} /* requires Application from dol */);
    }

    protected void generateArtifacts(DeploymentContext dc) throws DeploymentException {
        // Noting to generate yet!!
    }

    protected void cleanArtifacts(DeploymentContext dc) throws DeploymentException {
        // Drop tables if needed on undeploy.
        OpsParams params = dc.getCommandParameters(OpsParams.class);
        if (params.origin.isUndeploy()) {
            Java2DBProcessorHelper helper = new Java2DBProcessorHelper(dc);
            helper.init();
            helper.createOrDropTablesInDB(false, "JPA"); // NOI18N
        }
    }

    /**
     * @inheritDoc
     */
    public <V> V loadMetaData(Class<V> type, DeploymentContext context) {
        return null;
    }

    /**
     * EMFs for refered pus are created and stored in JPAApplication instance.
     * The JPAApplication instance is stored in given DeploymentContext to be retrieved by load
     */
    @Override public boolean prepare(DeploymentContext context) {
        boolean prepared = super.prepare(context);

            if(prepared) {
               Application application = context.getModuleMetaData(Application.class);
               Set<BundleDescriptor> bundles = application.getBundleDescriptors();

                // Iterate through all the bundles for the app and collect pu references in referencedPus
                List<PersistenceUnitDescriptor> referencedPus = new ArrayList<PersistenceUnitDescriptor>();
                for (BundleDescriptor bundle : bundles) {
                    Collection<? extends PersistenceUnitDescriptor> pusReferencedFromBundle = bundle.findReferencedPUs();
                    for(PersistenceUnitDescriptor pud : pusReferencedFromBundle) {
                        referencedPus.add(pud);
                    }
                }

                RootDeploymentDescriptor currentBundle = context.getModuleMetaData(BundleDescriptor.class);
                if (currentBundle == null) {
                    // We are being called for an application
                    currentBundle = application;
                }

                // Initialize pus for only this bundle here to enable transformers to work properly.
                // This is because classloader from deploymentcontext is for this bundle only
                Collection<PersistenceUnitsDescriptor> pusDescriptorForThisBundle = currentBundle.getExtensionsDescriptors(PersistenceUnitsDescriptor.class);
                for (PersistenceUnitsDescriptor persistenceUnitsDescriptor : pusDescriptorForThisBundle) {
                    for (PersistenceUnitDescriptor pud : persistenceUnitsDescriptor.getPersistenceUnitDescriptors()) {
                        if(referencedPus.contains(pud)) {
                            PersistenceUnitLoader puLoader = new PersistenceUnitLoader(pud, new ProviderContainerContractInfoImpl(context, connectorRuntime));
                            // Store the puLoader in context. It is retreived in load() to execute java2db and to
                            // store the loaded emfs in a JPAApplicationContainer object for cleanup
                            context.addTransientAppMetaData(getUniquePuIdentifier(pud), puLoader );
                        }
                    }
                }
            }

//        StatsProviderManager.register("jpa", PluginPoint.SERVER, "jpa/eclipselink", new EclipseLinkStatsProvider());

        return prepared;
    }

    /**
     * @inheritDoc
     */
    @Override
    public JPApplicationContainer load(JPAContainer container, DeploymentContext context) {
        RootDeploymentDescriptor currentBundle = context.getModuleMetaData(BundleDescriptor.class);
        if (currentBundle == null) {
            // We are being called for an application
            currentBundle = context.getModuleMetaData(Application.class);
        }

        List<EntityManagerFactory> emfsInitializedForThisBundle = new ArrayList<EntityManagerFactory>();
        Collection<PersistenceUnitsDescriptor> pusDescriptorForThisBundle = currentBundle.getExtensionsDescriptors(PersistenceUnitsDescriptor.class);
        for (PersistenceUnitsDescriptor persistenceUnitsDescriptor : pusDescriptorForThisBundle) {
            for (PersistenceUnitDescriptor pud : persistenceUnitsDescriptor.getPersistenceUnitDescriptors()) {
                //PersistenceUnitsDescriptor corresponds to  persistence.xml. A bundle can only have one persitence.xml except
                // when the bundle is an application which can have multiple persitence.xml under jars in root of ear and lib.
                PersistenceUnitLoader puLoader = context.getTransientAppMetaData(getUniquePuIdentifier(pud), PersistenceUnitLoader.class);
                if (puLoader != null) {
                    emfsInitializedForThisBundle.add(puLoader.getEMF());
                    puLoader.doJava2DB();
                }
            }
        }
        return new JPApplicationContainer(emfsInitializedForThisBundle);
    }

    /**
     * Returns unique identifier for this pu within application
     * @param pud The given pu
     * @return Absolute pu root + pu name
     */
    private static String getUniquePuIdentifier(PersistenceUnitDescriptor pud) {
        return pud.getAbsolutePuRoot() + pud.getName();
     }




    private static class ProviderContainerContractInfoImpl implements ProviderContainerContractInfo {
        private final DeploymentContext deploymentContext;
        private final ConnectorRuntime connectorRuntime;
        private final ClassLoader finalClassLoader;
        private ValidatorFactory validatorFactory;

        public ProviderContainerContractInfoImpl(DeploymentContext deploymentContext, ConnectorRuntime connectorRuntime) {
            this.deploymentContext = deploymentContext;
            this.connectorRuntime = connectorRuntime;
            // Cache finalClassLoader as deploymentContext.getFinalClassLoader() is expected to be called only once during deployment.
            this.finalClassLoader=deploymentContext.getFinalClassLoader();
        }

        public ClassLoader getClassLoader() {
            return finalClassLoader;
        }

        public ClassLoader getTempClassloader() {
            return ( (InstrumentableClassLoader)deploymentContext.getClassLoader() ).copy();
        }

        public void addTransformer(final ClassTransformer transformer) {
            // Bridge between java.lang.instrument.ClassFileTransformer that DeploymentContext accepts
            // and javax.persistence.spi.ClassTransformer that JPA supplies.
            deploymentContext.addTransformer(new ClassFileTransformer() {
                public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                                        ProtectionDomain protectionDomain, byte[] classfileBuffer)
                        throws IllegalClassFormatException {
                    return transformer.transform(loader, className, classBeingRedefined, protectionDomain, classfileBuffer);
                }
            });
        }

        /**
         * Returns Application location for current application
         * @return
         */
        public String getApplicationLocation() {
            // Get source for current bundle. If it has not parent, it is the top level application
            // else continute traversing up till we find one with not parent.
            ReadableArchive archive = deploymentContext.getSource();
            boolean appRootFound = false;
            while (!appRootFound) {
                ReadableArchive parentArchive = archive.getParentArchive();
                if(parentArchive != null) {
                    archive = parentArchive;
                } else {
                    appRootFound = true;
                }
            }
            return archive.getURI().getPath();
        }

        public DataSource lookupDataSource(String dataSourceName) throws NamingException {
            return DataSource.class.cast(connectorRuntime.lookupPMResource(dataSourceName, false) );
        }

        public DataSource lookupNonTxDataSource(String dataSourceName) throws NamingException {
            return DataSource.class.cast(connectorRuntime.lookupNonTxResource(dataSourceName, false));
        }

        public ValidatorFactory getValidatorFactory() {
            // TODO Once discussion about BeanValidation in JavaEE is done, ValidatorFactory should be available from deployment context
            // We only create one validator factory per bundle.
            if (validatorFactory == null) {
                validatorFactory = Validation.buildDefaultValidatorFactory();
            }

            return validatorFactory;
        }

        public boolean isJava2DBRequired() {
            OpsParams params = deploymentContext.getCommandParameters(OpsParams.class);
            // We only do java2db while being deployed on DAS. We do not do java2DB on load of an application or being deployed on an instance of a cluster
            // The method below gives us correct answers to handle above conditions
            return !params.origin.isArtifactsPresent();
        }

        public DeploymentContext getDeploymentContext() {
            return deploymentContext;
        }

        public void registerEMF(String unitName, String persistenceRootUri, RootDeploymentDescriptor containingBundle, EntityManagerFactory emf) {
            // We register the EMF into the bundle that declared the corresponding PU. This limits visibility of the emf
            // to containing module.
            // See EMFWrapper.lookupEntityManagerFactory() for corresponding look up logic
            if (containingBundle.isApplication()) {
                // ear level pu
                Application.class.cast(containingBundle).addEntityManagerFactory(
                        unitName, persistenceRootUri, emf);
            } else {
                BundleDescriptor.class.cast(containingBundle).addEntityManagerFactory(
                        unitName, emf);
            }
        }

        public String getJTADataSourceOverride() {
            return deploymentContext.getTransientAppMetaData(DatabaseConstants.JTA_DATASOURCE_JNDI_NAME_OVERRIDE, String.class);
        }

    } // class ProviderContainerContractInfoImpl

}
