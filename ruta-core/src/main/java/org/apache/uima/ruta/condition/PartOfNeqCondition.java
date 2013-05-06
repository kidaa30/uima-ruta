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

package org.apache.uima.ruta.condition;

import java.util.List;
import java.util.Set;

import org.apache.uima.cas.Type;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.ruta.RutaStream;
import org.apache.uima.ruta.expression.list.TypeListExpression;
import org.apache.uima.ruta.expression.type.TypeExpression;
import org.apache.uima.ruta.rule.EvaluatedCondition;
import org.apache.uima.ruta.rule.RuleElement;
import org.apache.uima.ruta.type.RutaBasic;
import org.apache.uima.ruta.visitor.InferenceCrowd;

public class PartOfNeqCondition extends TypeSentiveCondition {

  public PartOfNeqCondition(TypeExpression type) {
    super(type);
  }

  public PartOfNeqCondition(TypeListExpression list) {
    super(list);
  }

  @Override
  public EvaluatedCondition eval(AnnotationFS annotation, RuleElement element,
          RutaStream stream, InferenceCrowd crowd) {
    if (!isWorkingOnList()) {
      Type t = type.getType(element.getParent());
      boolean result = check(annotation, stream, t);
      return new EvaluatedCondition(this, result);
    } else {
      boolean result = false;
      List<Type> types = getList().getList(element.getParent());
      for (Type t : types) {
        result |= check(annotation, stream, t);
        if (result == true) {
          break;
        }
      }
      return new EvaluatedCondition(this, result);
    }
  }

  private boolean check(AnnotationFS annotation, RutaStream stream, Type t) {
    stream.moveTo(annotation);
    while (stream.isValid()) {
      RutaBasic each = (RutaBasic) stream.get();
      Set<AnnotationFS> set = each.getBeginAnchors(t);
      if(set == null)  {
        stream.moveToPrevious();
        continue;
      }
      for (AnnotationFS afs : set) {
        if (afs != null
                && afs.getType().equals(t)
                && ((afs.getBegin() < annotation.getBegin() && afs.getEnd() > annotation.getEnd())
                        || (afs.getBegin() == annotation.getBegin() && afs.getEnd() > annotation
                                .getEnd()) || (afs.getBegin() < annotation.getBegin() && afs
                        .getEnd() == annotation.getEnd()))) {
          return true;
        }

      }
      stream.moveToPrevious();
    }
    return false;
  }

}