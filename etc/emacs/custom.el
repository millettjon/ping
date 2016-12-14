;; My packages
(setq prelude-packages (append '(
                                 ; graphviz-dot-mode
                                 ) prelude-packages))
;; Install my packages
(prelude-install-packages)

;; Disable a few things that are too intrusive.
(setq prelude-whitespace nil)
(setq prelude-clean-whitespace-on-save nil)

;; smartparens - Use strict mode to act more like paredit.
(add-hook 'emacs-lisp-mode-hook       #'smartparens-strict-mode)
(add-hook 'clojure-mode-hook          #'smartparens-strict-mode)
