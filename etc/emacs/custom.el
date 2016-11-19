;; Disable a few things that are too intrusive.
(setq prelude-whitespace nil)
(setq prelude-clean-whitespace-on-save nil)

;; Setup paredit.
(add-hook 'emacs-lisp-mode-hook       #'enable-paredit-mode)
(add-hook 'clojure-mode-hook          #'enable-paredit-mode)
