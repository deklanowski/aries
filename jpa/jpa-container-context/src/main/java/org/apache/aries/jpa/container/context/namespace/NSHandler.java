/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.aries.jpa.container.context.namespace;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceContextType;

import org.apache.aries.blueprint.NamespaceHandler;
import org.apache.aries.blueprint.ParserContext;
import org.apache.aries.blueprint.PassThroughMetadata;
import org.apache.aries.blueprint.mutable.MutableBeanProperty;
import org.apache.aries.blueprint.reflect.BeanPropertyImpl;
import org.apache.aries.blueprint.reflect.ReferenceMetadataImpl;
import org.apache.aries.jpa.container.PersistenceUnitConstants;
import org.apache.aries.jpa.container.context.PersistenceManager;
import org.osgi.framework.Bundle;
import org.osgi.service.blueprint.reflect.BeanArgument;
import org.osgi.service.blueprint.reflect.BeanMetadata;
import org.osgi.service.blueprint.reflect.BeanProperty;
import org.osgi.service.blueprint.reflect.ComponentMetadata;
import org.osgi.service.blueprint.reflect.MapEntry;
import org.osgi.service.blueprint.reflect.MapMetadata;
import org.osgi.service.blueprint.reflect.Metadata;
import org.osgi.service.blueprint.reflect.Target;
import org.osgi.service.blueprint.reflect.ValueMetadata;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class NSHandler implements NamespaceHandler {
  
  public static final String NS_URI = "http://aries.apache.org/xmlns/jpa/v1.0.0";
  private static final String BLUEPRINT_NS = "http://www.osgi.org/xmlns/blueprint/v1.0.0";
  
  private static final String TAG_UNIT = "unit";
  private static final String TAG_CONTEXT = "context";
  private static final String TAG_MAP = "map";
  
  private static final String ATTR_PROPERTY = "property";
  private static final String ATTR_TYPE = "type";
  private static final String ATTR_UNIT_NAME = "unitname";
  
  private static final String TYPE_JTA = "TRANSACTION";
  private static final String DEFAULT_UNIT_NAME = "";
  
  public static final String EMPTY_UNIT_NAME_FILTER = 
    "(" + PersistenceUnitConstants.EMPTY_PERSISTENCE_UNIT_NAME + "=true)";
  
  private PersistenceManager manager;
  
  public void setManager(PersistenceManager manager) {
    this.manager = manager;
  }

  public ComponentMetadata decorate(Node node, ComponentMetadata component, ParserContext context) {
    if (node.getNodeType() != Node.ELEMENT_NODE)
      throw new IllegalArgumentException();    
    
    Element element = (Element) node;
    
    if (!(component instanceof BeanMetadata))
      throw new IllegalArgumentException();
    
    final BeanMetadata bean = (BeanMetadata) component;
    
    if (!NS_URI.equals(element.getNamespaceURI()))
      throw new IllegalArgumentException();
        
    if (!TAG_UNIT.equals(element.getLocalName()) && !TAG_CONTEXT.equals(element.getLocalName()))
      throw new IllegalArgumentException();
    
    final BeanProperty beanProperty = createInjectMetadata(element, 
        TAG_UNIT.equals(element.getLocalName()) ? EntityManagerFactory.class : EntityManager.class);
      
    if (TAG_CONTEXT.equals(element.getLocalName())) {
      Bundle client = getBlueprintBundle(context);
      String unitName = parseUnitName(element);

      HashMap<String,Object> properties = new HashMap<String, Object>();
      properties.put(ATTR_TYPE, parseType(element));
      properties.putAll(parseJPAProperties(element, context));

      manager.registerContext(unitName, client, properties);      
    }
    
    return new BeanMetadata() {
      
      public String getId() {
        return bean.getId();
      }
      
      public List<String> getDependsOn() {
        return bean.getDependsOn();
      }
      
      public int getActivation() {
        return bean.getActivation();
      }
      
      public String getScope() {
        return bean.getScope();
      }
      
      public List<BeanProperty> getProperties() {
        ArrayList<BeanProperty> result = new ArrayList<BeanProperty>(bean.getProperties());
        result.add(beanProperty);
        return result;
      }
      
      public String getInitMethod() {
        return bean.getInitMethod();
      }
      
      public String getFactoryMethod() {
        return bean.getFactoryMethod();
      }
      
      public Target getFactoryComponent() {
        return bean.getFactoryComponent();
      }
      
      public String getDestroyMethod() {
        return bean.getDestroyMethod();
      }
      
      public String getClassName() {
        return bean.getClassName();
      }
      
      public List<BeanArgument> getArguments() {
        return bean.getArguments();
      }
    };
  }

  public Set<Class> getManagedClasses() {
    return null;
  }

  public URL getSchemaLocation(String namespace) {
    return getClass().getResource("jpa.xsd");
  }

  public Metadata parse(Element element, ParserContext context) {
    /*
     * The namespace does not any top-level elements, so we should never get here.
     * In case we do -> explode.
     */
    throw new UnsupportedOperationException();
  }
  
  private BeanProperty createInjectMetadata(Element element, Class<?> clazz) {
    String unitName = parseUnitName(element);
    String property = parseProperty(element);

    ReferenceMetadataImpl refMetadata = new ReferenceMetadataImpl();
    refMetadata.setInterface(clazz.getName());
    if (!"".equals(unitName))
      refMetadata.setFilter("(" + PersistenceUnitConstants.OSGI_UNIT_NAME + "=" + unitName + ")");
    else
      refMetadata.setFilter(EMPTY_UNIT_NAME_FILTER);
    
    MutableBeanProperty propertyMetadata = new BeanPropertyImpl();
    propertyMetadata.setName(property);
    propertyMetadata.setValue(refMetadata);
    
    return propertyMetadata;
  }
  
  private Bundle getBlueprintBundle(ParserContext context) {
    PassThroughMetadata metadata = (PassThroughMetadata) context.getComponentDefinitionRegistry()
      .getComponentDefinition("blueprintBundle");
    
    Bundle result = null;
    if (metadata != null) {
      result = (Bundle) metadata.getObject();
    }
    
    return result;
  }
  
  private String parseProperty(Element element) {
    return element.getAttribute(ATTR_PROPERTY);
  }

  private PersistenceContextType parseType(Element element) {
    String typeName = element.hasAttribute(ATTR_TYPE) ? element.getAttribute(ATTR_TYPE) : TYPE_JTA;
    
    return PersistenceContextType.valueOf(typeName);
  }
  
  private String parseUnitName(Element element) {
    return element.hasAttribute(ATTR_UNIT_NAME) ? 
        element.getAttribute(ATTR_UNIT_NAME) : DEFAULT_UNIT_NAME;
  }
  
  private Map<String, Object> parseJPAProperties(Element element, ParserContext context) {
    Map<String, Object> result = new HashMap<String, Object>();
    NodeList ns = element.getElementsByTagNameNS(BLUEPRINT_NS, TAG_MAP);
    
    for (int i=0; i<ns.getLength(); i++) {
      MapMetadata metadata = context.parseElement(MapMetadata.class, null, (Element) ns.item(i));
      for (MapEntry entry : (List<MapEntry>) metadata.getEntries()) {
        if (entry.getKey() instanceof ValueMetadata && entry.getValue() instanceof ValueMetadata) {
          ValueMetadata key = (ValueMetadata) entry.getKey();
          ValueMetadata value = (ValueMetadata) entry.getValue();
          
          result.put(key.getStringValue(), value.getStringValue());
        } else {
          throw new UnsupportedOperationException();
        }
      }
    }
    
    return result;
  }
}
