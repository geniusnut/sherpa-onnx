// sherpa-onnx/csrc/tts-an2cn-test.cc
//
// Copyright (c)  2025  Xiaomi Corporation

#include "gtest/gtest.h"
#include "sherpa-onnx/csrc/an2cn.h"

namespace sherpa_onnx {

TEST(An2Cn, TestInteger) {
  EXPECT_EQ(ConvertNumbersToChinese("123"), "一百二十三");
  EXPECT_EQ(ConvertNumbersToChinese("0"), "零");
  EXPECT_EQ(ConvertNumbersToChinese("1001"), "一千零一");
  EXPECT_EQ(ConvertNumbersToChinese("10000"), "一万");
  EXPECT_EQ(ConvertNumbersToChinese("100000000"), "一亿");
  EXPECT_EQ(ConvertNumbersToChinese("-5"), "负五");
}

TEST(An2Cn, TestDecimal) {
  EXPECT_EQ(ConvertNumbersToChinese("1.23"), "一点二三");
  EXPECT_EQ(ConvertNumbersToChinese("0.5"), "零点五");
  EXPECT_EQ(ConvertNumbersToChinese("-3.14"), "负三点一四");
}

TEST(An2Cn, TestDate) {
  EXPECT_EQ(ConvertNumbersToChinese("2023年"), "二零二三年");
  EXPECT_EQ(ConvertNumbersToChinese("10月"), "十月");
  EXPECT_EQ(ConvertNumbersToChinese("1日"), "一日");
  EXPECT_EQ(ConvertNumbersToChinese("2023年10月1日"), "二零二三年十月一日");
}

TEST(An2Cn, TestFraction) {
  EXPECT_EQ(ConvertNumbersToChinese("1/2"), "二分之一");
  EXPECT_EQ(ConvertNumbersToChinese("3/4"), "四分之三");
}

TEST(An2Cn, TestPercent) {
  EXPECT_EQ(ConvertNumbersToChinese("50%"), "百分之五十");
  EXPECT_EQ(ConvertNumbersToChinese("12.5%"), "百分之十二点五");
  EXPECT_EQ(ConvertNumbersToChinese("-10%"), "负百分之十");
}

TEST(An2Cn, TestCelsius) {
  EXPECT_EQ(ConvertNumbersToChinese("25℃"), "二十五摄氏度");
  EXPECT_EQ(ConvertNumbersToChinese("-5℃"), "负五摄氏度");
}

TEST(An2Cn, TestMixed) {
  EXPECT_EQ(ConvertNumbersToChinese("今天是2023年10月1日，气温25℃。"),
            "今天是二零二三年十月一日，气温二十五摄氏度。");
  EXPECT_EQ(ConvertNumbersToChinese("增长了50%，达到了1.5倍。"),
            "增长了百分之五十，达到了一点五倍。");
}

}  // namespace sherpa_onnx
