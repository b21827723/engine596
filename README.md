# Engine596: Academic Search Engine

**Abstract**
---
We have developed an academic search engine that enables
users to conduct searches across various sections of articles.
Our system allows searching within titles, abstracts, or the
entire body of the article. Notably, even when a query is
performed on the entire article, the search results are ranked
based on distinct weighting factors assigned to matches
within different sections.

<img width="800" alt="poster" src="https://github.com/b21827723/engine596/assets/77360680/5ec0f39b-d15e-49c2-b543-5eaa4c54173e">


🔧 Usage
---
* Download the datasets from [this Google Drive link](https://drive.google.com/drive/folders/1MWB3xF0r795xySB2jym_HxhkJgg9MEHh?usp=sharing), i.e. CISI_Dataset and AAPR_Dataset. Note that these datasets are not the original ones but the preprocessed ones using `cisi_parse.py` and `aapr_parse.py`. Please refer to [this link for original CISI Dataset](https://www.kaggle.com/datasets/dmaso01dsta/cisi-a-dataset-for-information-retrieval) and [this link for original AAPR Dataset](https://github.com/lancopku/AAPR).
* Place the downloaded datasets (and if you prefer, downloaded indices -if not, Lucene will create it automatically-) under the same directory with the source code, i.e. AAPR_Dataset, CISI_Dataset, and engine596 folders will be sitting at the same directory.  
* Navigate to the main folder of the source code, i.e. src->main->java -> ... -> engine596 and run the source code.
* Use localhost:8080/ to run the main application.
* Use localhost:8080/eval to compute the evaluation metrics.

