import re
import os
import codecs
import tarfile
import json
import nltk

nltk.download('stopwords')
nltk.download('punkt')

from nltk.corpus import stopwords
from nltk.tokenize import word_tokenize

english_stopwords = stopwords.words('english')

def clean_math(string):
    while string.count('$') > 1:
        pos0 = string.find('$')
        pos1 = string.find('$', pos0 + 1)
        string = (string[:pos0] + string[pos1 + 1:]).strip()
    return string


def clean_str(string):
    # Remove mathematical formulas between $$
    string = clean_math(string)

    # Remove "ref"
    string = re.sub(r'~(.*)}', '', string)
    string = re.sub(r'\\cite(.*)}', '', string)
    string = re.sub(r'\\newcite(.*)}', '', string)
    string = re.sub(r'\\ref(.*)}', '', string)

    # Remove stopwords
    texts_tokenized = [word.lower() for word in word_tokenize(string) if word.isalpha()]
    texts_filtered_stopwords = [word for word in texts_tokenized if word not in english_stopwords]
    string = ' '.join(texts_filtered_stopwords)
    string = re.sub(r'[^\w\s]', '', string)
    return string


def process_text_list(text_list):
    result = []
    for line in text_list:
        line = line.strip()
        if line.startswith('%') or line.startswith('\\') or line == '':
            continue
        elif line[0].isdigit():
            continue
        else:
            result.append(clean_str(line))
    return result


# Extract Introduction, related work, etc.================================================================
def split(tex_list, start_char, end_char):
    lines = tex_list
    length = len(lines)
    start = None
    end = None
    i = 0
    while i < length and (end is None):
        if start is None:
            if lines[i].startswith(start_char):
                start = i + 1
        else:
            if lines[i].startswith(end_char):
                end = i
        i += 1
    if start is not None and end is None:
        end = length
    return lines[start:end]


def extract(tex_list):
    data = tex_list
    text = ' '.join(data)
    l = process_text_list(text.split('\n'))
    return ' '.join(l)


def main():
    files = ["data1, data2, data3, data4"]
    for file in files:
        with open(file, 'r') as f:
            data = json.load(f)

            results = []
            for i in data:
                tex_list = data[i]["tex_data"]
                results.append({"id": i, "abstract": data[i]["abstract"], "title": data[i]["title"], "text": extract(tex_list)})

            tot_words = sum(len(part) for result in results for part in result)
            print("Total words:", tot_words)
            print("Average:", tot_words // len(results))
            print(results[0])
            with open(file + "_parsed", 'w') as f:
                json.dump(results, f)


if __name__ == "__main__":
    main()
