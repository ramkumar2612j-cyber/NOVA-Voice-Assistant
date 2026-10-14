#!/usr/bin/env python3
"""Portable pure-logic mirror for the Kotlin SpecContract; no provider/network calls."""
import re, sys

def analyze(request):
    s=request.lower()
    denies_shop=any(x in s for x in ("no shop","no cart","no checkout","without a cart","without cart","without checkout","never ecommerce","never e-commerce","not ecommerce","not e-commerce","never shop","no shopping","cart nahi","checkout nahi","cart mat","checkout mat"))
    explicit=bool(re.search(r"\b(shop|store|ecommerce|e-commerce|dukaan|shopping)\b",s)) or any(x in s for x in ("online kapde","buy online","product listing","add to cart","shopping cart"))
    shop=(not denies_shop) and explicit
    if shop: kind="shop"
    elif any(x in s for x in ("blog","blogging","article","articles","blog post")): kind="blog"
    elif any(x in s for x in ("portfolio","resume","cv","personal website")): kind="portfolio"
    elif any(x in s for x in ("restaurant","cafe","menu","food outlet")): kind="restaurant"
    elif any(x in s for x in ("dashboard","admin panel","analytics panel")): kind="dashboard"
    elif any(x in s for x in ("download","apk download","app download","install app")): kind="app-download"
    elif any(x in s for x in ("web app","web application","interactive app","tool banao","calculator app")): kind="app"
    elif any(x in s for x in ("landing page","landing website","landing")): kind="landing"
    elif any(x in s for x in ("website","webpage","web page","site")): kind="landing"
    else: kind="other"
    styles=["3D"] if any(x in s for x in ("3d","three dimensional","three-dimensional","3-d")) else []
    mustnot=[]
    if kind in ("blog","portfolio","restaurant") and not shop: mustnot.append("shop/cart/checkout/product-price ecommerce interface")
    if kind!="shop" and not shop: mustnot.append("invented cart, checkout, buy-now flow, or product pricing")
    return kind, styles, mustnot

def check(spec, output):
    kind, styles, mustnot=spec; h=output.lower(); p=[]
    if kind=="blog" and not any(x in h for x in ("<article","blog post","blog-post",'class="post',"class='post","article-card","blog posts")):
        p.append("requested blog is missing recognizable article/post structure")
    if kind!="shop" and mustnot and any(x in h for x in ("shopping cart","add to cart","checkout","buy now","shopping-cart","cart-total","product price")):
        p.append("unrequested shop/cart/checkout/pricing signals found")
    if "3D" in styles and not any(x in h for x in ("perspective","preserve-3d","translatez(","webgl","<canvas","three.js","rotatex(","rotatey(")):
        p.append("3D requested but no detectable CSS 3D/canvas/WebGL implementation found")
    return p

CASES=[
 ("3d blogging website banao","blog",True,True),
 ("gym ki website banao","landing",False,True),
 ("online kapde ki dukaan ki website banao","shop",False,False),
 ("Build a dark 3D blog with 6 posts and #12abef","blog",True,True),
 ("photographer portfolio website","portfolio",False,True),
 ("restaurant website with menu","restaurant",False,True),
 ("apk download landing page","app-download",False,True),
 ("admin analytics dashboard","dashboard",False,True),
 ("minimal landing page for a nonprofit","landing",False,True),
 ("interactive calculator web app","app",False,True),
 ("3d portfolio site","portfolio",True,True),
 ("blogging platform, no cart","blog",False,True),
 ("online shoes shop with cart","shop",False,False),
 ("3D restaurant menu display","restaurant",True,True),
 ("personal resume website","portfolio",False,True),
 ("dashboard for solar panel monitoring","dashboard",False,True),
 ("simple one-page landing","landing",False,True),
 ("current research about phytoremediation dashboard","dashboard",False,True),
 ("create a café site with menu and contact","restaurant",False,True),
 ("create a blog with articles and no checkout","blog",False,True),
 ("make ecommerce storefront with prices","shop",False,False),
 ("make a 3D blogging website, never ecommerce","blog",True,True),
]
wrong='<!DOCTYPE html><html><head><style>.card{perspective:900px}</style></head><body><h1>Shop</h1><p>Product price</p><button>Add to cart</button><button>Checkout</button></body></html>'
failed=0
for request, expected_kind, expected_3d, expect_exclusion in CASES:
    spec=analyze(request)
    ok=(spec[0]==expected_kind and (('3D' in spec[1])==expected_3d) and ((bool(spec[2]))==expect_exclusion))
    print(f"{'OK' if ok else 'FAIL'} | {request} | kind={spec[0]} style={spec[1]} mustNot={bool(spec[2])}")
    failed += not ok
bad=check(analyze("3d blogging website banao"),wrong)
print(f"{'OK' if len(bad)>=2 else 'FAIL'} | deliberately wrong ecommerce output rejected | problems={bad}")
failed += not (len(bad)>=2)
if failed: sys.exit(1)
print(f"ALL PASSED: {len(CASES)} fixed sentences plus negative-output rejection")
