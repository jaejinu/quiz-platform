package com.quiz.domain.quiz;

public enum QuizType {
    SINGLE,    // 4지선다 단일 정답
    MULTIPLE,  // 복수 정답 (쉼표 구분 저장)
    OX,        // O/X
    SHORT      // 단답형 (텍스트 입력, 유사 정답 허용은 정답 판정 로직에서 처리)
}
