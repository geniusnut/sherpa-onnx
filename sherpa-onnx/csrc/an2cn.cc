// sherpa-onnx/csrc/an2cn.cc
//
// Copyright (c)  2025  Xiaomi Corporation

#include "sherpa-onnx/csrc/an2cn.h"

#include <regex>  // NOLINT
#include <vector>

#include "sherpa-onnx/csrc/text-utils.h"

namespace sherpa_onnx {

static std::wstring DigitsToChinese(const std::wstring &s) {
  std::wstring res;
  static const wchar_t *digits = L"零一二三四五六七八九";
  for (wchar_t c : s) {
    if (c >= L'0' && c <= L'9') {
      res += digits[c - L'0'];
    } else {
      res += c;
    }
  }
  return res;
}

static std::wstring IntegerToChinese(int64_t n) {
  if (n == 0) return L"零";
  std::wstring res;
  static const std::wstring digits = L"零一二三四五六七八九";
  static const std::wstring units = L"十百千";
  static const std::wstring big_units = L"万亿";

  if (n < 0) {
    res += L"负";
    n = -n;
  }

  std::vector<int> parts;
  while (n > 0) {
    parts.push_back(n % 10000);
    n /= 10000;
  }

  bool zero = false;
  for (int i = parts.size() - 1; i >= 0; --i) {
    int part = parts[i];
    if (part == 0) {
      zero = true;
      continue;
    }
    if (zero) {
      res += L"零";
      zero = false;
    }
    // Process 4 digits
    std::wstring p;
    int qian = part / 1000;
    int bai = (part % 1000) / 100;
    int shi = (part % 100) / 10;
    int ge = part % 10;

    if (qian > 0) {
      p += digits[qian];
      p += L"千";
    } else if (i < parts.size() - 1) {
      p += L"零";
    }

    if (bai > 0) {
      p += digits[bai];
      p += L"百";
    } else if (qian > 0 && (shi > 0 || ge > 0)) {
      p += L"零";
    }

    if (shi > 0) {
      if (shi == 1 && qian == 0 && bai == 0 && i == parts.size() - 1) {
        p += L"十";
      } else {
        p += digits[shi];
        p += L"十";
      }
    } else if (bai > 0 && ge > 0) {
      p += L"零";
    }

    if (ge > 0) p += digits[ge];

    // Remove duplicate zeros
    std::wstring clean_p;
    bool last_was_zero = false;
    for (wchar_t c : p) {
      if (c == L'零') {
        if (!last_was_zero) {
          clean_p += c;
          last_was_zero = true;
        }
      } else {
        clean_p += c;
        last_was_zero = false;
      }
    }
    res += clean_p;
    if (i > 0) res += big_units[i - 1];
  }

  // Final cleanup
  std::wstring final_res;
  bool last_was_zero = false;
  for (wchar_t c : res) {
    if (c == L'零') {
      if (!last_was_zero) {
        final_res += c;
        last_was_zero = true;
      }
    } else {
      final_res += c;
      last_was_zero = false;
    }
  }
  if (final_res.size() > 1 && final_res.back() == L'零') {
    final_res.pop_back();
  }
  return final_res;
}

static std::wstring NumberToChinese(const std::wstring &s) {
  size_t dot = s.find(L'.');
  if (dot != std::wstring::npos) {
    std::wstring integer_part = s.substr(0, dot);
    std::wstring decimal_part = s.substr(dot + 1);
    return IntegerToChinese(std::stoll(integer_part)) + L"点" +
           DigitsToChinese(decimal_part);
  } else {
    return IntegerToChinese(std::stoll(s));
  }
}

template <typename Func>
static std::wstring RegexReplace(const std::wstring &text,
                                 const std::wregex &re, Func callback) {
  std::wstring result;
  std::wsregex_iterator it(text.begin(), text.end(), re);
  std::wsregex_iterator end;
  size_t last_pos = 0;
  for (; it != end; ++it) {
    result += text.substr(last_pos, it->position() - last_pos);
    result += callback(*it);
    last_pos = it->position() + it->length();
  }
  result += text.substr(last_pos);
  return result;
}

std::string ConvertNumbersToChinese(const std::string &text) {
  std::wstring wtext = ToWideString(text);

  // 1. Date
  wtext = RegexReplace(wtext, std::wregex(LR"((\d+)年)"),
                       [](const std::wsmatch &m) {
                         return DigitsToChinese(m[1].str()) + L"年";
                       });
  wtext = RegexReplace(
      wtext, std::wregex(LR"((\d+)月)"), [](const std::wsmatch &m) {
        return IntegerToChinese(std::stoll(m[1].str())) + L"月";
      });
  wtext = RegexReplace(
      wtext, std::wregex(LR"((\d+)日)"), [](const std::wsmatch &m) {
        return IntegerToChinese(std::stoll(m[1].str())) + L"日";
      });

  // 2. Fraction
  wtext = RegexReplace(
      wtext, std::wregex(LR"((\d+)/(\d+))"), [](const std::wsmatch &m) {
        return IntegerToChinese(std::stoll(m[2].str())) + L"分之" +
               IntegerToChinese(std::stoll(m[1].str()));
      });

  // 3. Percent
  wtext = RegexReplace(wtext, std::wregex(LR"((-?)(\d+(\.\d+)?)%)"),
                       [](const std::wsmatch &m) {
                         std::wstring sign = m[1].str() == L"-" ? L"负" : L"";
                         return sign + L"百分之" + NumberToChinese(m[2].str());
                       });

  // 4. Celsius
  wtext = RegexReplace(
      wtext, std::wregex(LR"((-?)(\d+)℃)"), [](const std::wsmatch &m) {
        std::wstring sign = m[1].str() == L"-" ? L"负" : L"";
        return sign + IntegerToChinese(std::stoll(m[2].str())) + L"摄氏度";
      });

  // 5. Number
  wtext = RegexReplace(wtext, std::wregex(LR"((-?)(\d+(\.\d+)?))"),
                       [](const std::wsmatch &m) {
                         std::wstring sign = m[1].str() == L"-" ? L"负" : L"";
                         return sign + NumberToChinese(m[2].str());
                       });

  return ToString(wtext);
}

}  // namespace sherpa_onnx
