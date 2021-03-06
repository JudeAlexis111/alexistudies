/*
 * Copyright © 2017-2018 Harvard Pilgrim Health Care Institute (HPHCI) and its Contributors.
 * Copyright 2020-2021 Google LLC
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do so, subject to the
 * following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial
 * portions of the Software.
 *
 * Funding Source: Food and Drug Administration ("Funding Agency") effective 18 September 2014 as Contract no.
 * HHSF22320140030I/HHSF22301006T (the "Prime Contract").
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package com.fdahpstudydesigner.bo;

import java.io.Serializable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import org.hibernate.annotations.GenericGenerator;

@Entity
@Table(name = "comprehension_test_response")
public class ComprehensionTestResponseBo implements Serializable {

  private static final long serialVersionUID = 7739882770594873383L;

  @Column(name = "comprehension_test_question_id")
  private String comprehensionTestQuestionId;

  @Column(name = "correct_answer")
  private Boolean correctAnswer;

  @Id
  @GeneratedValue(generator = "system-uuid")
  @GenericGenerator(name = "system-uuid", strategy = "uuid")
  @Column(name = "id", updatable = false, nullable = false)
  private String id;

  @Column(name = "response_option")
  private String responseOption;

  @Column(name = "sequence_number")
  private Integer sequenceNumber;

  public String getComprehensionTestQuestionId() {
    return comprehensionTestQuestionId;
  }

  public Boolean getCorrectAnswer() {
    return correctAnswer;
  }

  public String getId() {
    return id;
  }

  public String getResponseOption() {
    return responseOption;
  }

  public void setComprehensionTestQuestionId(String comprehensionTestQuestionId) {
    this.comprehensionTestQuestionId = comprehensionTestQuestionId;
  }

  public void setCorrectAnswer(Boolean correctAnswer) {
    this.correctAnswer = correctAnswer;
  }

  public void setId(String id) {
    this.id = id;
  }

  public void setResponseOption(String responseOption) {
    this.responseOption = responseOption;
  }

  public Integer getSequenceNumber() {
    return sequenceNumber;
  }

  public void setSequenceNumber(Integer sequenceNumber) {
    this.sequenceNumber = sequenceNumber;
  }
}
