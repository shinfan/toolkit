/* Copyright 2017 Google Inc
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
package com.google.api.codegen.transformer.nodejs;

import com.google.api.codegen.metacode.InitCodeNode;
import com.google.api.codegen.transformer.GapicInterfaceContext;
import com.google.api.codegen.transformer.GapicMethodContext;
import com.google.api.codegen.transformer.GrpcStubTransformer;
import com.google.api.codegen.transformer.ImportSectionTransformer;
import com.google.api.codegen.transformer.StandardImportSectionTransformer;
import com.google.api.codegen.viewmodel.ImportFileView;
import com.google.api.codegen.viewmodel.ImportSectionView;
import com.google.api.codegen.viewmodel.ImportTypeView;
import com.google.api.tools.framework.model.Interface;
import com.google.common.collect.ImmutableList;
import java.util.Collections;
import java.util.List;

public class NodeJSImportSectionTransformer implements ImportSectionTransformer {
  @Override
  public ImportSectionView generateImportSection(GapicInterfaceContext context) {
    ImportSectionView.Builder importSection = ImportSectionView.newBuilder();
    importSection.externalImports(generateExternalImports(context));
    return importSection.build();
  }

  @Override
  public ImportSectionView generateImportSection(
      GapicMethodContext context, Iterable<InitCodeNode> specItemNodes) {
    return new StandardImportSectionTransformer().generateImportSection(context, specItemNodes);
  }

  private List<ImportFileView> generateExternalImports(GapicInterfaceContext context) {
    ImmutableList.Builder<ImportFileView> imports = ImmutableList.builder();
    Interface apiInterface = context.getInterface();
    String configModule = context.getNamer().getClientConfigPath(apiInterface);
    imports.add(createImport("configData", "./" + configModule));
    imports.add(createImport("extend", "extend"));
    imports.add(createImport("gax", "google-gax"));
    if (new GrpcStubTransformer().generateGrpcStubs(context).size() > 1) {
      imports.add(createImport("merge", "lodash.merge"));
    }
    return imports.build();
  }

  private ImportFileView createImport(String localName, String moduleName) {
    return ImportFileView.newBuilder()
        .types(
            Collections.singletonList(
                ImportTypeView.newBuilder().nickname(localName).fullName("").build()))
        .moduleName(moduleName)
        .build();
  }
}
