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

package org.apache.uima.ruta.rule;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.ruta.RutaBlock;
import org.apache.uima.ruta.RutaStream;
import org.apache.uima.ruta.action.AbstractRutaAction;
import org.apache.uima.ruta.condition.AbstractRutaCondition;
import org.apache.uima.ruta.rule.quantifier.RuleElementQuantifier;
import org.apache.uima.ruta.visitor.InferenceCrowd;

public class ComposedRuleElement extends AbstractRuleElement implements RuleElementContainer {

  protected List<RuleElement> elements;

  protected RuleElementContainer caretaker;

  public ComposedRuleElement(List<RuleElement> elements, RuleElementQuantifier quantifier,
          List<AbstractRutaCondition> conditions, List<AbstractRutaAction> actions,
          RuleElementContainer container, RutaBlock parent) {
    super(quantifier, conditions, actions, container, parent);
    this.elements = elements;
    this.caretaker = new RuleElementCaretaker(this);
  }

  public void apply(RuleMatch match, RutaStream symbolStream, InferenceCrowd crowd) {
    applyRuleElements(match, symbolStream, crowd);
    super.apply(match, symbolStream, crowd);
  }

  public void startMatch(RuleMatch ruleMatch, RuleApply ruleApply,
          ComposedRuleElementMatch containerMatch, RuleElement entryPoint, RutaStream stream,
          InferenceCrowd crowd) {
    RuleElement anchorElement = getAnchoringRuleElement(stream);
    ComposedRuleElementMatch composedMatch = createComposedMatch(ruleMatch, containerMatch);
    anchorElement.startMatch(ruleMatch, ruleApply, composedMatch, entryPoint, stream, crowd);
  }

  protected ComposedRuleElementMatch createComposedMatch(RuleMatch ruleMatch,
          ComposedRuleElementMatch containerMatch) {
    ComposedRuleElementMatch composedMatch = new ComposedRuleElementMatch(this, containerMatch);
    includeMatch(ruleMatch, containerMatch, composedMatch);
    return composedMatch;
  }

  public void continueMatch(boolean after, AnnotationFS annotation, RuleMatch ruleMatch,
          RuleApply ruleApply, ComposedRuleElementMatch containerMatch,
          RutaRuleElement sideStepOrigin, RuleElement entryPoint, RutaStream stream,
          InferenceCrowd crowd) {
    RuleElement nextElement = getNextElement(after, this);
    if (nextElement != null) {
      ComposedRuleElementMatch composedMatch = createComposedMatch(ruleMatch, containerMatch);
      nextElement.continueMatch(after, annotation, ruleMatch, ruleApply, composedMatch,
              sideStepOrigin, entryPoint, stream, crowd);
    } else {
      fallback(after, false, annotation, ruleMatch, ruleApply, containerMatch, sideStepOrigin,
              entryPoint, stream, crowd);
    }
  }

  public void continueOwnMatch(boolean after, AnnotationFS annotation, RuleMatch ruleMatch,
          RuleApply ruleApply, ComposedRuleElementMatch containerMatch,
          RutaRuleElement sideStepOrigin, RuleElement entryPoint, RutaStream stream,
          InferenceCrowd crowd) {
    if (!stream.isSimpleGreedyForComposed()) {
      continueMatch(after, annotation, ruleMatch, ruleApply, containerMatch, sideStepOrigin,
              entryPoint, stream, crowd);
    } else {
      // HOTFIX
      boolean stopMatching = false;
      boolean failed = false;
      AnnotationFS nextAnnotation = annotation;
      while (!stopMatching) {
        RuleElement nextElement = getNextElement(after, this);
        if (nextElement != null) {
          ComposedRuleElementMatch composedMatch = createComposedMatch(ruleMatch, containerMatch);
          nextElement.continueMatch(after, nextAnnotation, ruleMatch, ruleApply, composedMatch,
                  sideStepOrigin, this, stream, crowd);
          ComposedRuleElementMatch parentContainerMatch = containerMatch.getContainerMatch();
          List<RuleElementMatch> match = getMatch(ruleMatch, parentContainerMatch);
          int lenghtBefore = match.size();
          List<RuleElementMatch> evaluateMatches = quantifier.evaluateMatches(match, parent, crowd);
          ruleMatch.setMatched(ruleMatch.matched() && evaluateMatches != null);
          if (evaluateMatches.size() != lenghtBefore) {
            failed = true;
            stopMatching = true;
          }
          if (!quantifier.continueMatch(after, nextAnnotation, this, ruleMatch, containerMatch,
                  stream, crowd)) {
            stopMatching = true;
          }
          List<AnnotationFS> textsMatched = evaluateMatches.get(evaluateMatches.size() - 1)
                  .getTextsMatched();
          nextAnnotation = textsMatched.get(textsMatched.size() - 1);
        } else {
          stopMatching = true;
        }
      }
      fallback(after, failed, nextAnnotation, ruleMatch, ruleApply, containerMatch, sideStepOrigin,
              entryPoint, stream, crowd);
    }
  }

  public void fallbackContinue(boolean after, boolean failed, AnnotationFS annotation,
          RuleMatch ruleMatch, RuleApply ruleApply, ComposedRuleElementMatch containerMatch,
          RutaRuleElement sideStepOrigin, RuleElement entryPoint, RutaStream stream,
          InferenceCrowd crowd) {
    RuleElementContainer container = getContainer();
    doMatch(containerMatch, stream, crowd);
    if (this.equals(entryPoint) && ruleApply == null) {
      return;
    }
    if (container == null) {
      fallback(after, failed, annotation, ruleMatch, ruleApply, containerMatch, sideStepOrigin,
              entryPoint, stream, crowd);
    } else {
      ComposedRuleElementMatch parentContainerMatch = containerMatch.getContainerMatch();
      RuleElement nextElement = container.getNextElement(after, this);
      List<RuleElementMatch> match = getMatch(ruleMatch, parentContainerMatch);
      boolean continueMatch = quantifier.continueMatch(after, annotation, this, ruleMatch,
              parentContainerMatch, stream, crowd);
      List<RuleElementMatch> evaluateMatches = quantifier.evaluateMatches(match, parent, crowd);
      ruleMatch.setMatched(ruleMatch.matched() && (evaluateMatches != null || continueMatch));
      if (failed) {
        if (nextElement != null) {
          AnnotationFS backtrackedAnnotation = getBacktrackedAnnotation(evaluateMatches);
          if (backtrackedAnnotation != null) {
            nextElement.continueMatch(after, backtrackedAnnotation, ruleMatch, ruleApply,
                    parentContainerMatch, sideStepOrigin, null, stream, crowd);
          } else {
            fallback(after, failed, annotation, ruleMatch, ruleApply, parentContainerMatch,
                    sideStepOrigin, entryPoint, stream, crowd);
          }
        } else {
          fallback(after, failed, annotation, ruleMatch, ruleApply, parentContainerMatch,
                  sideStepOrigin, entryPoint, stream, crowd);
        }
      } else {
        if (continueMatch) {
          continueOwnMatch(after, annotation, ruleMatch, ruleApply, parentContainerMatch,
                  sideStepOrigin, null, stream, crowd);
        } else if (nextElement != null) {
          nextElement.continueMatch(after, annotation, ruleMatch, ruleApply, parentContainerMatch,
                  sideStepOrigin, null, stream, crowd);
        } else {
          fallback(after, failed, annotation, ruleMatch, ruleApply, parentContainerMatch,
                  sideStepOrigin, entryPoint, stream, crowd);
        }
      }
    }
  }

  private AnnotationFS getBacktrackedAnnotation(List<RuleElementMatch> evaluateMatches) {
    if (evaluateMatches == null) {
      return null;
    }
    // TODO both directions!
    List<AnnotationFS> textsMatched = evaluateMatches.get(evaluateMatches.size() - 1)
            .getTextsMatched();
    if (textsMatched.isEmpty()) {
      return null;
    }
    AnnotationFS backtrackedAnnotation = textsMatched.get(textsMatched.size() - 1);
    return backtrackedAnnotation;
  }

  private void fallback(boolean after, boolean failed, AnnotationFS annotation,
          RuleMatch ruleMatch, RuleApply ruleApply, ComposedRuleElementMatch containerMatch,
          RutaRuleElement sideStepOrigin, RuleElement entryPoint, RutaStream stream,
          InferenceCrowd crowd) {
    RuleElementContainer parentContainer = getContainer();
    if (parentContainer instanceof ComposedRuleElement) {
      ComposedRuleElement parentElement = (ComposedRuleElement) parentContainer;
      parentElement.fallbackContinue(after, failed, annotation, ruleMatch, ruleApply,
              containerMatch, sideStepOrigin, entryPoint, stream, crowd);
    } else if (sideStepOrigin != null) {
      sideStepOrigin.continueSideStep(after, ruleMatch, ruleApply, containerMatch, entryPoint,
              stream, crowd);
    } else {
      doneMatching(ruleMatch, ruleApply, stream, crowd);
    }
  }

  private void includeMatch(RuleMatch ruleMatch, ComposedRuleElementMatch containerMatch,
          ComposedRuleElementMatch composedMatch) {
    if (containerMatch == null) {
      ruleMatch.setRootMatch(composedMatch);
    } else {
      containerMatch.addInnerMatch(this, composedMatch, false);
    }
  }

  private void doMatch(ComposedRuleElementMatch match, RutaStream stream, InferenceCrowd crowd) {
    List<AnnotationFS> textsMatched = match.getTextsMatched();
    if (textsMatched == null || textsMatched.isEmpty()) {
      return;
    }
    int begin = textsMatched.get(0).getBegin();
    int end = textsMatched.get(textsMatched.size() - 1).getEnd();
    AnnotationFS annotation = stream.getCas().createAnnotation(stream.getCas().getAnnotationType(),
            begin, end);

    List<EvaluatedCondition> evaluatedConditions = new ArrayList<EvaluatedCondition>(
            conditions.size());
    for (AbstractRutaCondition condition : conditions) {
      crowd.beginVisit(condition, null);
      EvaluatedCondition eval = condition.eval(annotation, this, stream, crowd);
      crowd.endVisit(condition, null);
      evaluatedConditions.add(eval);
    }
    match.setConditionInfo(evaluatedConditions);
    match.evaluateInnerMatches(true);
  }

  public Collection<AnnotationFS> getAnchors(RutaStream stream) {
    RuleElement anchorElement = getAnchoringRuleElement(stream);
    Collection<AnnotationFS> anchors = anchorElement.getAnchors(stream);
    return anchors;
  }

  public int estimateAnchors(RutaStream stream) {
    int result = 1;
    for (RuleElement each : elements) {
      result += each.estimateAnchors(stream);
    }
    return result;
  }

  public RuleElement getAnchoringRuleElement(RutaStream stream) {
    return caretaker.getAnchoringRuleElement(stream);
  }

  public List<RuleElement> getRuleElements() {
    return elements;
  }

  public void setRuleElements(List<RuleElement> elements) {
    this.elements = elements;
  }

  public RuleElement getFirstElement() {
    return caretaker.getFirstElement();
  }

  public RuleElement getLastElement() {
    return caretaker.getLastElement();
  }

  public void applyRuleElements(RuleMatch ruleMatch, RutaStream stream, InferenceCrowd crowd) {
    caretaker.applyRuleElements(ruleMatch, stream, crowd);
  }

  public String toString() {
    String simpleName = getQuantifier().getClass().getSimpleName();
    return "(" + (elements == null ? "null" : elements.toString()) + ")"
            + (simpleName.equals("NormalQuantifier") ? "" : simpleName)
            + (conditions.isEmpty() ? "" : "(" + conditions.toString() + ")" + "\\n")
            + (actions.isEmpty() ? "" : "{" + actions.toString() + "}");
  }

  public RuleElement getNextElement(boolean after, RuleElement ruleElement) {
    return caretaker.getNextElement(after, ruleElement);
  }

}
