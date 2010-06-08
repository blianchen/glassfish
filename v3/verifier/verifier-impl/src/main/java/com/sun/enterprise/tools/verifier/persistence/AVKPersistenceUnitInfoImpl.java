/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 1997-2010 Sun Microsystems, Inc. All rights reserved.
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

package com.sun.enterprise.tools.verifier.persistence;

import com.sun.enterprise.deployment.PersistenceUnitDescriptor;
import com.sun.enterprise.deployment.RootDeploymentDescriptor;

import javax.sql.DataSource;
import javax.persistence.spi.ClassTransformer;
import javax.persistence.EntityManagerFactory;
import javax.naming.NamingException;
import javax.validation.ValidatorFactory;

import org.glassfish.persistence.jpa.PersistenceUnitInfoImpl;
import org.glassfish.persistence.jpa.ProviderContainerContractInfo;
import org.glassfish.api.deployment.InstrumentableClassLoader;
import org.glassfish.api.deployment.DeploymentContext;

/**
 * This class implements {@link javax.persistence.spi.PersistenceUnitInfo}
 * It inherits most of the implementation from its super class, except the
 * implementation that depends on runtime environment. See the details of methods
 * overridden in this class.
 *
 * @author Sanjeeb.Sahoo@Sun.COM
 */
public class AVKPersistenceUnitInfoImpl extends PersistenceUnitInfoImpl
{
    public AVKPersistenceUnitInfoImpl(
            PersistenceUnitDescriptor persistenceUnitDescriptor,
            final String applicationLocation,
            final InstrumentableClassLoader classLoader) {
        super(persistenceUnitDescriptor, new ProviderContainerContractInfo() {
            public ClassLoader getClassLoader()
            {
                return (ClassLoader)classLoader;
            }

            public ClassLoader getTempClassloader()
            {
                return null;
            }

            public void addTransformer(ClassTransformer transformer)
            {
                // NOOP
            }

            public String getApplicationLocation()
            {
                return applicationLocation;
            }

            public DataSource lookupDataSource(String dataSourceName) throws NamingException
            {
                return null;
            }

            public DataSource lookupNonTxDataSource(String dataSourceName) throws NamingException
            {
                return null;
            }

            public ValidatorFactory getValidatorFactory() {
                // TODO: Need to implement this correctly.
                return null;
            }

            public DeploymentContext getDeploymentContext()
            {
                return null;
            }

            public boolean isJava2DBRequired()
            {
                return false;
            }

            public void registerEMF(String unitName, String persistenceRootUri,
                                    RootDeploymentDescriptor containingBundle,
                                    EntityManagerFactory emf)
            {
                // NOOP
            }

            @Override
            public String getJTADataSourceOverride() {
                return null;
            }
        }
        );
    }

}
