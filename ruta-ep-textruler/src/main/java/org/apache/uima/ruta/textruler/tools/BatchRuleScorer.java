/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.uima.ruta.textruler.tools;

import java.io.File;
import java.io.IOException;

import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.ruta.textruler.TextRulerPlugin;
import org.apache.uima.ruta.textruler.core.TextRulerToolkit;
import org.apache.uima.ruta.textruler.tools.F1Scorer.Score;
import org.apache.uima.util.FileUtils;

public class BatchRuleScorer {

  private static String engineFile;

  // private static String preprocessorTMFile;
  private static String foldRootDirectory;

  private static int foldCount = 0;

  private static AnalysisEngine ae = null;

  public static void main(String[] args) {
    engineFile = "/Users/tobi/Documents/UniLaptop/Diplomarbeit/TestDataSets/withPosTags/Subset100/10fold/desc/lp2ergebnisrandomgiantEngine.xml";
    foldRootDirectory = "/Users/tobi/Documents/UniLaptop/Diplomarbeit/TestDataSets/withPosTags/9010_middle/";// mit
    foldCount = 1;
    String slotNames[] = { "org.apache.uima.ml.types.etime", "org.apache.uima.ml.types.stime",
        "org.apache.uima.ml.types.location", "org.apache.uima.ml.types.speaker" };
    String algIDs[] = { "optimizedLP2"// ,
    // "rapier",
    // "whisk"
    };

    ae = TextRulerToolkit.loadAnalysisEngine(engineFile);

    // TODO back to 0 !
    for (int foldNumber = 0; foldNumber < foldCount; foldNumber++) {
      for (String slotName : slotNames) {
        for (String algID : algIDs) {
          scoreRules(foldNumber, slotName, algID);
        }
      }
    }

  }

  public static void scoreRules(int foldNumber, String slotName, String algorithmID) {
    TextRulerToolkit.log("Scoring Fold Number " + foldNumber + "\t  Slot: " + slotName
            + "\t  Algorithm: " + algorithmID);

    String fromRulesFolder = foldRootDirectory + foldNumber + "/testing/markedFromRules/"
            + slotName + "/" + algorithmID + "/";
    String originalsFolder = foldRootDirectory + foldNumber + "/testing/withtags/";
    String resultsFile = foldRootDirectory + foldNumber + "/testing/score_" + foldNumber + "_"
            + algorithmID + "." + slotName + ".txt";
    if (!new File(fromRulesFolder).exists()) {
      TextRulerToolkit.log("\tSKIPPED, fromRulesFolder not found!");
      return;
    }
    F1Scorer scorer = new F1Scorer(ae, originalsFolder, fromRulesFolder);
    Score slotScore = scorer.scoreSlot(slotName);
    String str = "Score Results for Fold " + foldNumber + "\t  Slot: " + slotName
            + "\t  Algorithm: " + algorithmID + "\n\n";
    str += slotScore.toString();
    try {
      FileUtils.saveString2File(str, new File(resultsFile));
    } catch (IOException e) {
      TextRulerPlugin.error(e);
    }
  }

}