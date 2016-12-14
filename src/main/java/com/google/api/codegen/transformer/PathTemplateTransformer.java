/* Copyright 2016 Google Inc
 *
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
 */
package com.google.api.codegen.transformer;

import com.google.api.codegen.InterfaceView;
import com.google.api.codegen.ResourceNameTreatment;
import com.google.api.codegen.config.FieldConfig;
import com.google.api.codegen.config.FixedResourceNameConfig;
import com.google.api.codegen.config.InterfaceConfig;
import com.google.api.codegen.config.ResourceNameConfig;
import com.google.api.codegen.config.ResourceNameMessageConfigs;
import com.google.api.codegen.config.ResourceNameOneofConfig;
import com.google.api.codegen.config.ResourceNameType;
import com.google.api.codegen.config.SingleResourceNameConfig;
import com.google.api.codegen.util.Name;
import com.google.api.codegen.viewmodel.FormatResourceFunctionView;
import com.google.api.codegen.viewmodel.ParseResourceFunctionView;
import com.google.api.codegen.viewmodel.PathTemplateArgumentView;
import com.google.api.codegen.viewmodel.PathTemplateGetterFunctionView;
import com.google.api.codegen.viewmodel.PathTemplateView;
import com.google.api.codegen.viewmodel.ResourceIdParamView;
import com.google.api.codegen.viewmodel.ResourceNameFixedView;
import com.google.api.codegen.viewmodel.ResourceNameOneofView;
import com.google.api.codegen.viewmodel.ResourceNameParamView;
import com.google.api.codegen.viewmodel.ResourceNameSingleView;
import com.google.api.codegen.viewmodel.ResourceNameView;
import com.google.api.codegen.viewmodel.ResourceProtoFieldView;
import com.google.api.codegen.viewmodel.ResourceProtoView;
import com.google.api.tools.framework.model.Field;
import com.google.api.tools.framework.model.Interface;
import com.google.api.tools.framework.model.MessageType;
import com.google.api.tools.framework.model.ProtoFile;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/** PathTemplateTransformer generates view objects for path templates from a service model. */
public class PathTemplateTransformer {

  public List<PathTemplateView> generatePathTemplates(SurfaceTransformerContext context) {
    List<PathTemplateView> pathTemplates = new ArrayList<>();
    if (!context.getFeatureConfig().enableStringFormatFunctions()) {
      return pathTemplates;
    }
    InterfaceConfig interfaceConfig = context.getInterfaceConfig();
    for (SingleResourceNameConfig resourceNameConfig :
        interfaceConfig.getSingleResourceNameConfigs()) {
      PathTemplateView.Builder pathTemplate = PathTemplateView.newBuilder();
      pathTemplate.name(
          context.getNamer().getPathTemplateName(context.getInterface(), resourceNameConfig));
      pathTemplate.pattern(resourceNameConfig.getNamePattern());
      pathTemplates.add(pathTemplate.build());
    }

    return pathTemplates;
  }

  public List<ResourceNameView> generateResourceNames(SurfaceTransformerContext context) {
    return generateResourceNames(context, context.getApiConfig().getResourceNameConfigs().values());
  }

  public List<ResourceNameView> generateResourceNames(
      SurfaceTransformerContext context, Iterable<ResourceNameConfig> configs) {
    List<ResourceNameView> resourceNames = new ArrayList<>();
    int index = 1;
    for (ResourceNameConfig config : configs) {
      switch (config.getResourceNameType()) {
        case SINGLE:
          resourceNames.add(
              generateResourceNameSingle(context, index, (SingleResourceNameConfig) config));
          break;
        case ONEOF:
          resourceNames.add(
              generateResourceNameOneof(context, index, (ResourceNameOneofConfig) config));
          break;
        case FIXED:
          resourceNames.add(
              generateResourceNameFixed(context, index, (FixedResourceNameConfig) config));
          break;
        default:
          throw new IllegalStateException("Unexpected resource-name type.");
      }
      index += 1;
    }
    return resourceNames;
  }

  private ResourceNameSingleView generateResourceNameSingle(
      SurfaceTransformerContext context, int index, SingleResourceNameConfig config) {
    SurfaceNamer namer = context.getNamer();
    ResourceNameSingleView.Builder builder =
        ResourceNameSingleView.newBuilder()
            .typeName(namer.getResourceTypeName(config))
            .paramName(namer.getResourceParameterName(config))
            .propertyName(namer.getResourcePropertyName(config))
            .enumName(namer.getResourceEnumName(config))
            .docName(config.getEntityName())
            .index(index)
            .pattern(config.getNamePattern());
    List<ResourceNameParamView> params = new ArrayList<>();
    int varIndex = 0;
    for (String var : config.getNameTemplate().vars()) {
      ResourceNameParamView.Builder paramBuilder =
          ResourceNameParamView.newBuilder()
              .index(varIndex++)
              .nameAsParam(namer.getParamName(var))
              .nameAsProperty(namer.getPropertyName(var))
              .docName(namer.getParamDocName(var));
      params.add(paramBuilder.build());
    }
    builder.params(params);
    return builder.build();
  }

  private ResourceNameOneofView generateResourceNameOneof(
      SurfaceTransformerContext context, int index, ResourceNameOneofConfig config) {
    SurfaceNamer namer = context.getNamer();
    ResourceNameOneofView.Builder builder =
        ResourceNameOneofView.newBuilder()
            .typeName(namer.getResourceTypeName(config))
            .paramName(namer.getResourceParameterName(config))
            .propertyName(namer.getResourcePropertyName(config))
            .enumName(namer.getResourceEnumName(config))
            .docName(config.getEntityName())
            .index(index)
            .children(generateResourceNames(context, config.getResourceNameConfigs()));
    return builder.build();
  }

  private ResourceNameFixedView generateResourceNameFixed(
      SurfaceTransformerContext context, int index, FixedResourceNameConfig config) {
    SurfaceNamer namer = context.getNamer();
    ResourceNameFixedView.Builder builder =
        ResourceNameFixedView.newBuilder()
            .typeName(namer.getResourceTypeName(config))
            .paramName(namer.getResourceParameterName(config))
            .propertyName(namer.getResourcePropertyName(config))
            .enumName(namer.getResourceEnumName(config))
            .docName(config.getEntityName())
            .index(index)
            .value(config.getFixedValue());
    return builder.build();
  }

  public List<ResourceProtoView> generateResourceProtos(SurfaceTransformerContext context) {
    SurfaceNamer namer = context.getNamer();
    ResourceNameMessageConfigs resourceConfigs =
        context.getApiConfig().getResourceNameMessageConfigs();
    Map<String, ResourceNameConfig> resourceNameConfigs =
        context.getApiConfig().getResourceNameConfigs();
    ListMultimap<String, Field> fieldsByMessage = ArrayListMultimap.create();
    Set<String> seenProtoFiles = new HashSet<>();
    for (Interface service : new InterfaceView().getElementIterable(context.getModel())) {
      ProtoFile protoFile = service.getFile();
      if (!seenProtoFiles.contains(protoFile.getSimpleName())) {
        seenProtoFiles.add(protoFile.getSimpleName());
        for (MessageType msg : protoFile.getMessages()) {
          for (Field field : msg.getFields()) {
            if (resourceConfigs.fieldHasResourceName(field)) {
              fieldsByMessage.put(msg.getFullName(), field);
            }
          }
        }
      }
    }
    List<ResourceProtoView> protos = new ArrayList<>();
    for (Entry<String, Collection<Field>> entry : fieldsByMessage.asMap().entrySet()) {
      String msgName = entry.getKey();
      Collection<Field> fields = new ArrayList<Field>(entry.getValue());
      ResourceProtoView.Builder protoBuilder = ResourceProtoView.newBuilder();
      protoBuilder.protoClassName(namer.getTypeNameConverter().getTypeName(msgName).getNickname());
      List<ResourceProtoFieldView> fieldViews = new ArrayList<>();
      for (Field field : fields) {
        String fieldName = field.getSimpleName();
        FieldConfig fieldConfig =
            FieldConfig.createMessageFieldConfig(
                resourceConfigs, resourceNameConfigs, field, ResourceNameTreatment.STATIC_TYPES);
        String fieldTypeSimpleName = namer.getResourceTypeName(fieldConfig.getResourceNameConfig());
        String fieldTypeName =
            context
                .getTypeTable()
                .getAndSaveNicknameForTypedResourceName(fieldConfig, fieldTypeSimpleName);
        if (field.getType().isRepeated()) {
          fieldTypeName = fieldTypeName.replaceFirst("IEnumerable", "IList");
        }
        String fieldDocTypeName = fieldTypeName.replace('<', '{').replace('>', '}');
        String fieldElementTypeName =
            context
                .getTypeTable()
                .getAndSaveNicknameForResourceNameElementType(fieldConfig, fieldTypeSimpleName);
        ResourceProtoFieldView fieldView =
            ResourceProtoFieldView.newBuilder()
                .typeName(fieldTypeName)
                .parseMethodTypeName(namer.getPackageName() + "." + fieldTypeName)
                .docTypeName(fieldDocTypeName)
                .elementTypeName(fieldElementTypeName)
                .isAny(fieldConfig.getResourceNameType() == ResourceNameType.ANY)
                .isRepeated(field.getType().isRepeated())
                .isOneof(fieldConfig.getResourceNameType() == ResourceNameType.ONEOF)
                .propertyName(namer.getResourceNameFieldGetFunctionName(fieldConfig))
                .underlyingPropertyName(namer.publicMethodName(Name.from(fieldName)))
                .build();
        fieldViews.add(fieldView);
      }
      protoBuilder.fields(fieldViews);
      protos.add(protoBuilder.build());
    }
    Collections.sort(
        protos,
        new Comparator<ResourceProtoView>() {
          @Override
          public int compare(ResourceProtoView a, ResourceProtoView b) {
            return a.protoClassName().compareTo(b.protoClassName());
          }
        });
    return protos;
  }

  public List<FormatResourceFunctionView> generateFormatResourceFunctions(
      SurfaceTransformerContext context) {
    List<FormatResourceFunctionView> functions = new ArrayList<>();
    if (!context.getFeatureConfig().enableStringFormatFunctions()) {
      return functions;
    }

    SurfaceNamer namer = context.getNamer();
    Interface service = context.getInterface();
    InterfaceConfig interfaceConfig = context.getInterfaceConfig();
    for (SingleResourceNameConfig resourceNameConfig :
        interfaceConfig.getSingleResourceNameConfigs()) {
      FormatResourceFunctionView.Builder function =
          FormatResourceFunctionView.newBuilder()
              .entityName(resourceNameConfig.getEntityName())
              .name(namer.getFormatFunctionName(service, resourceNameConfig))
              .pathTemplateName(namer.getPathTemplateName(service, resourceNameConfig))
              .pathTemplateGetterName(namer.getPathTemplateNameGetter(service, resourceNameConfig))
              .pattern(resourceNameConfig.getNamePattern());
      List<ResourceIdParamView> resourceIdParams = new ArrayList<>();
      for (String var : resourceNameConfig.getNameTemplate().vars()) {
        ResourceIdParamView param =
            ResourceIdParamView.newBuilder()
                .name(namer.getParamName(var))
                .docName(namer.getParamDocName(var))
                .templateKey(var)
                .build();
        resourceIdParams.add(param);
      }
      function.resourceIdParams(resourceIdParams);

      functions.add(function.build());
    }

    return functions;
  }

  public List<ParseResourceFunctionView> generateParseResourceFunctions(
      SurfaceTransformerContext context) {
    List<ParseResourceFunctionView> functions = new ArrayList<>();
    if (!context.getFeatureConfig().enableStringFormatFunctions()) {
      return functions;
    }

    SurfaceNamer namer = context.getNamer();
    Interface service = context.getInterface();
    InterfaceConfig interfaceConfig = context.getInterfaceConfig();
    for (SingleResourceNameConfig resourceNameConfig :
        interfaceConfig.getSingleResourceNameConfigs()) {
      for (String var : resourceNameConfig.getNameTemplate().vars()) {
        ParseResourceFunctionView.Builder function =
            ParseResourceFunctionView.newBuilder()
                .entityName(resourceNameConfig.getEntityName())
                .name(namer.getParseFunctionName(var, resourceNameConfig))
                .pathTemplateName(namer.getPathTemplateName(service, resourceNameConfig))
                .pathTemplateGetterName(
                    namer.getPathTemplateNameGetter(service, resourceNameConfig))
                .entityNameParamName(namer.getEntityNameParamName(resourceNameConfig))
                .outputResourceId(var);
        functions.add(function.build());
      }
    }

    return functions;
  }

  public List<PathTemplateGetterFunctionView> generatePathTemplateGetterFunctions(
      SurfaceTransformerContext context) {
    List<PathTemplateGetterFunctionView> functions = new ArrayList<>();

    SurfaceNamer namer = context.getNamer();
    Interface service = context.getInterface();
    InterfaceConfig interfaceConfig = context.getInterfaceConfig();
    for (SingleResourceNameConfig resourceNameConfig :
        interfaceConfig.getSingleResourceNameConfigs()) {
      PathTemplateGetterFunctionView.Builder function =
          PathTemplateGetterFunctionView.newBuilder()
              .name(namer.getPathTemplateNameGetter(service, resourceNameConfig))
              .resourceName(namer.getPathTemplateResourcePhraseName(resourceNameConfig))
              .pathTemplateName(namer.getPathTemplateName(service, resourceNameConfig))
              .pattern(resourceNameConfig.getNamePattern());

      List<PathTemplateArgumentView> args = new ArrayList<>();
      for (String templateKey : resourceNameConfig.getNameTemplate().vars()) {
        String name = context.getNamer().localVarName(Name.from(templateKey));
        args.add(PathTemplateArgumentView.newBuilder().templateKey(templateKey).name(name).build());
      }
      function.args(args);
      functions.add(function.build());
    }

    return functions;
  }
}
